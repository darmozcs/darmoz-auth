# Runbook de deploy — darmoz-auth

Referencia de lo que se hizo (y cómo repetirlo) para poner `darmoz-auth` en
`darmoz@192.168.1.23`, siguiendo `DEPLOY_GUIDE.md`. Estado real relevado del
servidor antes de escribir esto:

- Contenedores activos: `traefik`, `postgres` (db/user `nexora`), `nexora-api`
  (prefijo `/api`), `nexora` (frontend).
- Redes: `proxy`, `data` (ambas ya existen como externas).
- Convención real de env vars por servicio: prefijo `<SERVICIO>_` (ej.
  `NEXORA_API_DOMAIN`, `NEXORA_API_PATH_PREFIX`, `NEXORA_API_PORT`) — acá
  usamos `DARMOZ_AUTH_*` para mantener el mismo patrón.
- Runners ya registrados: `darmozcs-nexora-be` y `darmozcs-nexora-ui`. No
  existía uno para `darmoz-auth`.
- Ambiente de desarrollo: se reutiliza el par de llaves RSA de `keys-dev/`
  (no se genera un par nuevo "de producción" porque este server es el
  ambiente de dev del proyecto).

## Pasos

1. `mkdir -p /opt/infra/services/darmoz-auth/keys`
2. Subir `keys-dev/private_key.pem` y `keys-dev/public_key.pem` a
   `/opt/infra/services/darmoz-auth/keys/` (`scp`, nunca por git).
3. Copiar `deploy/compose.yaml` → `/opt/infra/services/darmoz-auth/compose.yaml`.
4. Copiar `deploy/.env.example` → `/opt/infra/services/darmoz-auth/.env`,
   completar y `chmod 600`.
5. Renombrar la rama local `master` → `main` (`git branch -M main`) antes del
   primer push — el workflow dispara sobre `main`.
6. Registrar el runner self-hosted para este repo:
   ```bash
   mkdir -p ~/actions-runner-darmoz-auth && cd ~/actions-runner-darmoz-auth
   curl -o runner.tar.gz -L https://github.com/actions/runner/releases/download/v<version>/actions-runner-linux-x64-<version>.tar.gz
   tar xzf runner.tar.gz
   ./config.sh --url https://github.com/darmozcs/darmoz-auth --token <TOKEN> \
     --unattended --name darmoz-server-auth --labels self-hosted,linux,x64 --work _work
   sudo ./svc.sh install darmoz
   sudo ./svc.sh start
   ```
   El `<TOKEN>` sale de GitHub → repo `darmozcs/darmoz-auth` → Settings →
   Actions → Runners → New self-hosted runner (dura ~1h, solo lo puede
   generar quien tiene acceso admin al repo).
7. `git push` a `main` → dispara CI: test → build & push a GHCR → deploy
   (`docker compose pull && up -d` en el server vía el runner).
8. Si `docker compose pull` da `denied`: hacer público el paquete en
   `github.com/darmozcs?tab=packages` → `darmoz-auth` → Package settings →
   Public (o `docker login ghcr.io` en el server con un token
   `read:packages`).
9. Verificar: `curl -sk https://darmozsc.duckdns.org/auth/actuator/health` y
   `docker logs darmoz-auth --tail 50`.

## Nota de seguridad

El acceso SSH a este servidor se configuró en esta sesión agregando una
clave pública dedicada (`claude-code-darmoz-auth-deploy`) a
`~/.ssh/authorized_keys` del usuario `darmoz`, después de que la contraseña
se compartiera en el chat. Conviene rotar esa contraseña y, si ya no hace
falta el acceso automatizado, remover esa entrada de `authorized_keys`.
