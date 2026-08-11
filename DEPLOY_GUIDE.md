# Guía de infraestructura y deploy — darmoz@192.168.1.23

Todo lo que se armó para Nexora, pensado como plantilla reutilizable: cualquier
servicio nuevo (backend, frontend, base de datos, cola) sigue el mismo patrón.

---

## 1. Arquitectura general

```
Internet
   │  (80/443, reenviados por el router a 192.168.1.23)
   ▼
Traefik  (único punto de entrada, TLS con Let's Encrypt)
   │
   ├── red "proxy"  (todo lo que Traefik debe poder rutear)
   │      ├── nexora        (frontend, /nexora)
   │      └── nexora-api    (backend, /api)
   │
   └── nexora-api también está en la red "data" (interna, sin Traefik)
          └── postgres      (sin exponer a Traefik ni a internet)
```

- **Una carpeta por servicio** en `/opt/infra/services/<nombre>/`, cada una con
  su propio `compose.yaml` + `.env`. Se puede levantar sola
  (`cd services/<nombre> && docker compose up -d`) sin tocar nada más.
- **Descubrimiento automático**: Traefik mira los contenedores Docker vía
  labels. Si un contenedor tiene `traefik.enable=true` y está en la red
  `proxy`, Traefik lo rutea solo — no hay que reiniciar Traefik ni editar
  ningún archivo suyo al agregar un servicio.
- **Dos redes Docker externas**:
  - `proxy` → para todo lo que necesita ser alcanzable desde afuera (vía Traefik).
  - `data` → para servicios internos (bases de datos, colas) que **nunca**
    deben tener labels de Traefik ni quedar expuestos.
- **CI/CD**: cada repo de GitHub tiene su propio runner self-hosted corriendo
  como servicio systemd en este mismo servidor. `git push` a `main` → build +
  push a GHCR (en la nube de GitHub) → el runner local hace
  `docker compose pull && up -d` **solo** de ese servicio.

---

## 2. Cosas que NO hay que olvidar

1. **El router (ZTE) debe reenviar 80 y 443 (TCP) a `192.168.1.23`.** Si algún
   día deja de funcionar el sitio desde afuera, lo primero es chequear esto —
   nos pasó que el router respondía con su propio panel de admin en el 443 en
   vez de reenviarlo.
2. **Cada repo de GitHub necesita su propio runner self-hosted.** Un runner
   registrado a `nexora-be` no sirve para `nexora-ui` ni para ningún repo
   nuevo — hay que repetir el registro (ver sección 6). Si migran a una
   organización de GitHub, ahí sí se puede compartir un runner entre repos.
3. **Los paquetes de GHCR nacen privados.** Si `docker compose pull` da
   `denied` (no `not found`), es por visibilidad, no porque falte la imagen.
   Se arregla en `github.com/<usuario>?tab=packages` → paquete → *Package
   settings* → Public. (O `docker login ghcr.io` en el server con un token
   `read:packages` si prefieren mantenerlo privado.)
4. **Nada de bases de datos/colas expuesto directo a internet.** Solo se
   publican puertos atados a la IP de LAN (`192.168.1.23:PUERTO:PUERTO`),
   nunca `0.0.0.0` ni reenviados en el router. Para administrar desde afuera
   de casa: túnel SSH, no abrir el puerto.
5. **Todos los `.env` con secretos: `chmod 600` y nunca se commitean a git**
   (ya están en `.gitignore`/no versionados). Viven solo en el servidor.
6. **Flyway (o cualquier migración de esquema) es solo hacia adelante.** Nunca
   editar un archivo de migración ya aplicado — Flyway lo detecta por
   checksum y se niega a arrancar. Para corregir algo, migración nueva.
7. **Antes de levantar Traefik en un server nuevo, revisar la versión.** Si el
   Docker daemon es muy nuevo, versiones viejas de Traefik (ej. v3.1) fallan
   con `client version X.XX is too old` al hablar con el socket de Docker —
   subir de versión (`traefik:v3.7` o más nueva) lo resuelve.
8. **Postgres usa volumen nombrado (`postgres_data`), no bind mount.** Backup
   antes de migraciones destructivas:
   ```bash
   docker exec postgres pg_dump -U nexora nexora > backup_$(date +%F).sql
   ```

---

## 3. Deployar un backend nuevo (paso a paso)

Ejemplo real: `nexora-api` (Spring Boot). Sirve para cualquier stack (Node,
Python, Go, etc.) — solo cambia el `Dockerfile`.

1. **Repo en GitHub** (vacío, lo crea el usuario) + proyecto local con:
   - `Dockerfile` (multi-stage: build + imagen runtime liviana).
   - `.github/workflows/deploy.yml` (ver plantilla sección 6).
2. **En el servidor**, nueva carpeta:
   ```bash
   mkdir -p /opt/infra/services/<app>
   ```
3. `services/<app>/compose.yaml`:
   ```yaml
   services:
     <app>:
       image: ${APP_IMAGE:-ghcr.io/<usuario>/<app>:latest}
       container_name: <app>
       restart: unless-stopped
       env_file: .env
       networks:
         - proxy
         # - data   (agregar solo si necesita hablarle a una base de datos)
       labels:
         - "traefik.enable=true"
         - "traefik.docker.network=proxy"
         - "traefik.http.routers.<app>.rule=Host(`${APP_DOMAIN}`) && PathPrefix(`${APP_PATH_PREFIX}`)"
         - "traefik.http.routers.<app>.entrypoints=websecure"
         - "traefik.http.routers.<app>.tls.certresolver=letsencrypt"
         - "traefik.http.routers.<app>.middlewares=secure-headers@file"
         - "traefik.http.services.<app>.loadbalancer.server.port=${APP_PORT}"

   networks:
     proxy:
       external: true
     # data:
     #   external: true
   ```
4. `services/<app>/.env`:
   ```
   APP_IMAGE=ghcr.io/<usuario>/<app>:latest
   APP_DOMAIN=darmozsc.duckdns.org
   APP_PATH_PREFIX=/<lo-que-sea>
   APP_PORT=8080
   ```
   `chmod 600` si tiene secretos (passwords de DB, API keys, etc.).
5. Push al repo → CI construye, publica en GHCR y deploya (ver sección 6).
6. Verificar:
   ```bash
   curl -sk https://darmozsc.duckdns.org/<path>/actuator/health   # o el healthcheck que tenga
   docker logs <app> --tail 50
   ```

---

## 4. Deployar un frontend nuevo (paso a paso)

Ejemplo real: `nexora-ui` (React + Vite + nginx).

1. `vite.config.ts` (o el bundler que sea): `base: '/<path>/'` solo en build,
   para que los assets resuelvan bien detrás del PathPrefix.
2. `Dockerfile` multi-stage: build con node, runtime con `nginx:alpine`.
3. `nginx.conf` con el patrón `alias` + `try_files` para servir bajo un
   subpath y soportar rutas de SPA:
   ```nginx
   location = /<path> { return 301 /<path>/; }
   location /<path>/ {
       alias /usr/share/nginx/html/;
       try_files $uri $uri/ /<path>/index.html;
   }
   ```
4. Mismo patrón de `compose.yaml`/`.env` que un backend (sección 3), con
   `APP_PORT=80` (el puerto que expone nginx) y sin red `data` (un frontend
   estático no habla directo con la base).
5. Si consume una API en el mismo dominio, usar **fetch a rutas absolutas**
   (`/api/...`), nunca hardcodear el dominio — mismo origen, sin CORS.
6. En dev, proxyar `/api` en `vite.config.ts` (`server.proxy`) al backend
   real, para poder probar con datos reales sin desplegar nada.

---

## 5. Agregar una base de datos nueva

Mismo patrón, aislada en la red `data`, sin tocar Traefik.

```yaml
name: <nombre-db>

services:
  <nombre-db>:
    image: postgres:16-alpine   # o mysql, mongo, etc.
    container_name: <nombre-db>
    restart: unless-stopped
    env_file: .env
    ports:
      - "192.168.1.23:<puerto>:<puerto>"   # opcional, solo si necesitan admin desde la LAN
    volumes:
      - <nombre-db>_data:/var/lib/postgresql/data
    networks:
      - data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER"]
      interval: 10s
      timeout: 5s
      retries: 5

networks:
  data:
    external: true

volumes:
  <nombre-db>_data:
```

El backend que la consuma se conecta por **nombre de contenedor** como host
(ej. `jdbc:postgresql://<nombre-db>:5432/...`), siempre que ambos estén en la
red `data`. Nunca hace falta IP fija ni exponer el puerto para que se
comuniquen entre sí.

---

## 6. Agregar una cola (RabbitMQ, Redis, etc.)

Idéntico patrón que una base de datos — otra carpeta en `services/`, sin
Traefik, en la red `data` (o una red `broker` dedicada si se prefiere separar
tráfico de mensajería del de bases de datos).

```yaml
name: rabbitmq

services:
  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: rabbitmq
    restart: unless-stopped
    env_file: .env
    networks:
      - data
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    # Panel de administración (15672): NO exponerlo a internet directo.
    # Si se necesita, ruteo por Traefik con basicauth (ver README, sección
    # dashboard de Traefik) o túnel SSH, igual que con Postgres.

networks:
  data:
    external: true

volumes:
  rabbitmq_data:
```

Los consumidores/productores se conectan como `amqp://rabbitmq:5672` desde
cualquier contenedor en la misma red `data`.

---

## 7. CI/CD para un repo nuevo (self-hosted runner)

Cada repo necesita:

**a) Workflow** (`.github/workflows/deploy.yml`):
```yaml
name: Deploy <app>

on:
  push: { branches: [main] }
  workflow_dispatch: {}

env:
  IMAGE: ghcr.io/${{ github.repository_owner }}/<app>

permissions:
  contents: read
  packages: write

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: "${{ github.actor }}", password: "${{ secrets.GITHUB_TOKEN }}" }
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE }}:latest
            ${{ env.IMAGE }}:${{ github.sha }}

  deploy:
    needs: build-and-push
    runs-on: self-hosted   # el runner de ESTE repo específico
    steps:
      - run: |
          cd /opt/infra/services/<app>
          docker compose pull
          docker compose up -d
          docker image prune -f
```

**b) Runner self-hosted** (en el servidor, una vez por repo):
```bash
mkdir -p ~/actions-runner-<app> && cd ~/actions-runner-<app>
curl -o runner.tar.gz -L https://github.com/actions/runner/releases/download/v<version>/actions-runner-linux-x64-<version>.tar.gz
tar xzf runner.tar.gz

# Token: Settings → Actions → Runners → New self-hosted runner (dura ~1h)
./config.sh --url https://github.com/<usuario>/<repo> --token <TOKEN> \
  --unattended --name darmoz-server-<app> --labels self-hosted,linux,x64 --work _work

sudo ./svc.sh install darmoz
sudo ./svc.sh start
sudo ./svc.sh status     # debe decir "active (running)"
```

Nada más que hacer: el servicio queda `enabled` (arranca solo si el server
reinicia) y se auto-actualiza cuando está inactivo.

---

## 8. Troubleshooting — cosas que ya pasaron acá

| Síntoma | Causa | Solución |
|---|---|---|
| Traefik: `client version 1.24 is too old` | Docker daemon muy nuevo, Traefik viejo | Subir el tag de imagen de Traefik (probamos v3.1 → v3.7) |
| `docker compose pull` da `denied` | Paquete de GHCR privado | Hacerlo público, o `docker login ghcr.io` con token `read:packages` |
| Runner de GitHub Actions: `dial tcp ...:22 i/o timeout` | El job corría en `ubuntu-latest` (nube) intentando SSH a una IP privada | Cambiar a `runs-on: self-hosted` |
| Certificado real emitido pero el sitio no abre desde afuera | Puerto 443 no reenviado (o reenviado a otro dispositivo) en el router | Revisar la regla de NAT del router; comparar el `issuer` del certificado visto desde afuera vs. `openssl s_client -connect localhost:443` en el propio server |
| Error 400 raro con texto "SessionTimeout" al pegarle al dominio | El router (ZTE) respondía con su propio panel admin en vez de reenviar | Mismo fix que el de arriba — comparar certificados para confirmar quién responde realmente |

---

## 9. Comandos de referencia rápida

```bash
# Todo el stack
cd /opt/infra && docker compose up -d

# Un servicio puntual, aislado
cd /opt/infra/services/<app> && docker compose up -d

# Logs
docker logs -f <container>

# Estado de un runner
sudo systemctl status actions.runner.<usuario>-<repo>.<nombre>.service

# Backup rápido de Postgres
docker exec postgres pg_dump -U nexora nexora > backup_$(date +%F).sql

# Túnel para administrar una DB desde afuera de la LAN
ssh -L 5432:localhost:5432 darmoz@192.168.1.23
```
