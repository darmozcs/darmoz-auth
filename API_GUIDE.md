# Guía de integración — darmoz-auth

Microservicio de autenticación centralizado (JWT RSA / RS256). Este documento
describe todo lo necesario para integrar un cliente (frontend, backend, u
otro microservicio) sin tener que leer el código fuente.

---

## 1. Base URL

| Entorno | Base URL |
|---|---|
| Producción/dev (server) | `https://darmozsc.duckdns.org/auth` |
| Local | `http://localhost:8080/auth` |

El prefijo `/auth` es parte fija de la ruta (`server.servlet.context-path`),
no algo que agregue el proxy. Todos los endpoints de este documento son
relativos a esa base — ej. `POST /auth/login`.

Content-Type de request y response: `application/json` en todos los casos.

---

## 2. Modelo de autenticación

Dos tipos de token, devueltos juntos en cada login/registro/refresh:

- **`accessToken`** — JWT firmado con RSA (RS256), de vida corta (default
  **720 segundos / 12 min**, configurable). Va en el header
  `Authorization: Bearer <accessToken>` de cada request a un recurso
  protegido. Es **autocontenido**: incluye el user id, email, roles y
  permisos ya resueltos, así que un servicio consumidor puede leerlo sin
  necesariamente llamar de vuelta a darmoz-auth (ver sección 5).
- **`refreshToken`** — string opaco (no es un JWT, no se puede decodificar),
  de vida larga (default **30 días**, configurable). Se usa una sola vez:
  cada `POST /refresh` lo consume y devuelve un `accessToken` nuevo **y un
  `refreshToken` nuevo** (rotación). Se guarda hasheado (SHA-256) en la base,
  nunca en texto plano.

### Rotación y detección de robo de refresh token

Cada refresh token es de un solo uso. Si se reutiliza uno ya usado/rotado
(señal de que alguien más lo capturó), el servidor:
1. Revoca **todos** los refresh tokens activos de ese usuario (todas las
   sesiones, en todos los dispositivos).
2. Responde `401` a esa llamada.

**Importante para el cliente:** no dispares dos `POST /refresh` en paralelo
con el mismo `refreshToken` (ej. dos tabs, o una carrera entre requests que
refrescan al detectar 401). Serializar/deduplicar el refresh es
responsabilidad del cliente — el servidor no tiene forma de saber que dos
llamadas simultáneas son "el mismo" intento legítimo.

---

## 3. Estructura del Access Token (JWT)

Algoritmo: **RS256** (RSASSA-PKCS1-v1_5 + SHA-256), asimétrico. darmoz-auth
firma con su llave **privada**; cualquier consumidor valida con la llave
**pública** correspondiente.

### Claims

```json
{
  "jti": "b3e1f9a0-2c1a-4e77-9c1e-6f2a2f9d6a11",
  "sub": "0f3a2b1c-...-uuid-del-usuario",
  "email": "usuario@ejemplo.com",
  "roles": ["USER"],
  "permissions": [
    { "service": "nexora-api", "method": "GET", "path": "/api/products/**" }
  ],
  "typ": "access",
  "iss": "darmoz-auth",
  "iat": 1770000000,
  "exp": 1770000720
}
```

| Claim | Significado |
|---|---|
| `jti` | ID único del token (UUID). Se usa para poder revocarlo individualmente en logout. |
| `sub` | UUID del usuario (`User.id`). |
| `email` | Email del usuario. |
| `roles` | Array de roles (`USER`, `ADMIN`; ver sección 4). |
| `permissions` | Lista de permisos resueltos **al momento de emitir el token** (ver sección 4). Cambios de permisos en la base no afectan tokens ya emitidos hasta el próximo `login`/`refresh`. |
| `typ` | Siempre `"access"` (reservado por si en el futuro se emiten otros tipos de token). |
| `iss` | Issuer, configurable (default `darmoz-auth`). |
| `iat` / `exp` | Unix timestamp (segundos) de emisión / expiración. |

### Cómo validar el token

**Header y firma:** estándar JWT (`alg: RS256`), generado con la librería
`jjwt`. Cualquier librería JWT estándar en cualquier lenguaje puede
verificarlo con la llave pública.

**Formato de las llaves:**
- Privada: PKCS#8 (`-----BEGIN PRIVATE KEY-----`). Generada con
  `openssl genpkey -algorithm RSA`, **no** `openssl genrsa` (formato PKCS#1,
  incompatible).
- Pública: X.509 SubjectPublicKeyInfo (`-----BEGIN PUBLIC KEY-----`).

**Distribución de la llave pública:** actualmente **no hay un endpoint JWKS
público** (`/.well-known/jwks.json` o similar). La llave pública se
distribuye fuera de banda (archivo compartido, variable de entorno, etc.).
Llave pública del entorno de desarrollo actual (`keys-dev/public_key.pem`):

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmtesHsKYDNBLdl+nZZkh
GAV/wTpkFG5/ttmS/piVm9THja80MnCH2ly3ZL+XXrHgOck1QWH7rnEXIWCo2uyH
dalmcJViVnNecELGnEtzgIX/TuInX+DAz6jH0mF8lxSb4kmN47iiFM3bICGxr0ea
MHc7ye+jNYO/EEyd4DuaaQsgtYxu8u5dCPyFNSjMM5v4zeEvAkn1r6wnWgZf/2AX
wgOT230TTOZ7nUzfgcnS7tMZUf0BjrDjDf4+x4Ta1LAItJhEm+JqnB7PmIIkzq3c
UYUoZecQ2rtuQJat+Y3j4VaMekHA9S0IMbKT7M6ymRi9uTdpD5d8Ie6XFtv2ERoW
nQIDAQAB
-----END PUBLIC KEY-----
```

⚠️ Es la llave de **desarrollo**, compartida por conveniencia entre todos
los servicios que corren contra el mismo servidor de dev. No asumir que es
estable a largo plazo ni usarla como referencia para un entorno productivo
separado.

**Dos formas de validar un token, con distinto trade-off:**

| Estrategia | Cómo | Pro | Contra |
|---|---|---|---|
| **Validación local** | El servicio consumidor valida firma+`exp`+`iss` con la llave pública, localmente, con cualquier librería JWT. | Sin latencia de red, sin acoplar disponibilidad al auth service. | No se entera de revocaciones server-side (logout) hasta que el token expira naturalmente — ver sección 6. |
| **Validación remota** | `POST /auth/verify` con el token, en cada request (o cacheado unos segundos). | Se entera de revocaciones al instante. | Latencia + dependencia de red hacia darmoz-auth. |

Para la mayoría de los casos (ventana de expiración corta, 12 min default),
la validación local es suficiente. Si se necesita invalidación inmediata al
hacer logout, usar `/verify` o mantener una blocklist propia consultando
revocaciones.

### Ejemplo de verificación local (pseudo-código / Node con `jose`)

```js
import { jwtVerify, importSPKI } from 'jose';

const publicKey = await importSPKI(PEM_PUBLIC_KEY, 'RS256');

const { payload } = await jwtVerify(accessToken, publicKey, {
  issuer: 'darmoz-auth',
});
// payload.sub, payload.email, payload.roles, payload.permissions
```

### Ejemplo de verificación local (Java, sin dependencias del proyecto)

```java
KeyFactory kf = KeyFactory.getInstance("RSA");
PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(derBytes));

Jws<Claims> jws = Jwts.parser()
        .verifyWith(publicKey)
        .requireIssuer("darmoz-auth")
        .build()
        .parseSignedClaims(accessToken);
```

---

## 4. Roles y permisos

- **Roles**: enum fijo, actualmente `USER` y `ADMIN` (`com.darmoz.auth.entity.Role`).
  Todo usuario nuevo (`/register`) se crea con `USER` únicamente — no hay
  forma de auto-asignarse `ADMIN` vía API.
- **Permisos**: tabla `auth_role_permissions`, mapea `role → (service,
  http_method, endpoint_pattern)`. Representa qué puede hacer cada rol en
  **otros** servicios (no en darmoz-auth mismo). Ejemplo de fila:

  | role | service | http_method | endpoint_pattern |
  |---|---|---|---|
  | USER | nexora-api | GET | /api/products/** |

  Esto significa: "un usuario con rol USER puede hacer GET a
  `/api/products/**` en el servicio `nexora-api`". `endpoint_pattern` usa
  sintaxis estilo Ant (`**`, `*`).

- Los permisos de un usuario son la **unión deduplicada** de los permisos de
  todos sus roles, resuelta en el momento del login/register/refresh y
  embebida en el JWT como el claim `permissions` (ver sección 3). Un
  servicio consumidor que reciba el token puede autorizar la request
  localmente comparando `(service, method, path)` contra su propio nombre
  de servicio + el método/path de la request entrante, **sin llamar a
  darmoz-auth**.
- Alta de nuevas filas en `auth_role_permissions` (o de nuevos roles) es
  actualmente solo vía acceso directo a la base — no hay endpoint de admin
  para gestionarlos.

---

## 5. Endpoints

### `POST /auth/register`

Crea un usuario nuevo con rol `USER` y devuelve tokens (equivalente a
registrarse + loguearse en un solo paso).

**Body:**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "unaPasswordSegura123"
}
```
Validación: `email` requerido y con formato válido; `password` requerido,
entre 8 y 100 caracteres.

**200 → 201 Created:**
```json
{
  "userId": "0f3a2b1c-4d5e-4f6a-8b7c-9d0e1f2a3b4c",
  "email": "usuario@ejemplo.com",
  "roles": ["USER"],
  "permissions": [
    { "service": "nexora-api", "method": "GET", "path": "/api/products/**" }
  ],
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "8f3a2b1c4d5e4f6a8b7c9d0e1f2a3b4c...",
  "tokenType": "Bearer",
  "expiresIn": 720
}
```

**Errores:**
| Status | Cuándo | Body |
|---|---|---|
| `400` | email inválido / password fuera de rango / campos vacíos | `{"message": "email: must be a well-formed email address; password: size must be between 8 and 100", ...}` |
| `409` | el email ya está registrado | `{"message": "Ya existe una cuenta con ese email", ...}` |

---

### `POST /auth/login`

**Body:**
```json
{ "email": "usuario@ejemplo.com", "password": "unaPasswordSegura123" }
```

**200 OK:** mismo shape que `register` (`AuthResponse`, ver arriba).

**Errores:**
| Status | Cuándo | Body |
|---|---|---|
| `400` | campos vacíos | validación |
| `401` | email no existe, password incorrecta, o cuenta deshabilitada (`enabled=false`) | `{"message": "Email o password invalidos", ...}` |

Nota: el mensaje es idéntico para "no existe", "password mal" y "cuenta
deshabilitada" — deliberado, para no filtrar si un email está registrado.

---

### `POST /auth/refresh`

Rota el refresh token (revoca el usado, emite uno nuevo) y emite un access
token nuevo con roles/permisos **recalculados en ese momento** (útil si
cambiaron los roles del usuario desde el login original).

**Body:**
```json
{ "refreshToken": "8f3a2b1c4d5e4f6a8b7c9d0e1f2a3b4c..." }
```

**200 OK:** `AuthResponse` completo, con `accessToken` y `refreshToken`
**nuevos**. El `refreshToken` recibido queda inválido inmediatamente.

**Errores (todos 401):**
| Body `message` | Causa |
|---|---|
| `Refresh token invalido` | no existe / no corresponde a ningún hash en la base |
| `Refresh token expirado` | pasó el TTL (default 30 días) |
| `Refresh token reusado; se revocaron todas las sesiones` | el token ya había sido usado antes → se revocaron **todas** las sesiones activas del usuario, hay que loguear de nuevo |

---

### `POST /auth/logout`

Revoca el refresh token (body) y, si se manda el access token, también lo
agrega a la blocklist de revocados (por `jti`) para que `/verify` y
cualquier chequeo de revocación lo vean como inválido de inmediato.

**Headers:** `Authorization: Bearer <accessToken>` — **opcional pero
recomendado**. Si no se manda, solo se revoca el refresh token; el access
token sigue siendo válido (en validación local) hasta que expira solo.

**Body:**
```json
{ "refreshToken": "8f3a2b1c4d5e4f6a8b7c9d0e1f2a3b4c..." }
```

**204 No Content** — siempre, sea el token válido, inválido o inexistente
(operación idempotente, no filtra información).

**Errores:** solo `400` si `refreshToken` viene vacío/ausente.

---

### `POST /auth/verify`

Valida un access token del lado del servidor: firma, expiración, y si está
en la blocklist de revocados. Pensado para que otro servicio delegue la
validación completa (incluida revocación) en darmoz-auth en vez de validar
localmente.

**Headers:** `Authorization: Bearer <accessToken>` — opcional; si falta,
responde `valid: false, reason: "missing_token"`.

Sin body.

**200 OK — siempre** (este endpoint **nunca** devuelve 401/403 por un token
inválido; el resultado va en el body):

Token válido:
```json
{
  "valid": true,
  "userId": "0f3a2b1c-4d5e-4f6a-8b7c-9d0e1f2a3b4c",
  "email": "usuario@ejemplo.com",
  "roles": ["USER"],
  "permissions": [
    { "service": "nexora-api", "method": "GET", "path": "/api/products/**" }
  ],
  "expiresAt": "2026-08-11T23:09:19Z",
  "reason": null
}
```

Token inválido:
```json
{
  "valid": false,
  "userId": null,
  "email": null,
  "roles": null,
  "permissions": null,
  "expiresAt": null,
  "reason": "expired"
}
```

`reason` posibles: `missing_token`, `expired`, `invalid_signature`,
`malformed`, `revoked`.

---

## 6. Formato de error genérico

Todos los errores (`400`, `401`, `409`) que no sean de `/verify` (que
siempre devuelve `200`) usan este shape:

```json
{
  "timestamp": "2026-08-11T22:57:19.298Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Email o password invalidos"
}
```

---

## 7. CORS

Configurado en `SecurityConfig`:
- **Origins permitidos:** `https://darmozsc.duckdns.org` únicamente (hardcodeado, no hay wildcard).
- **Métodos:** `GET`, `POST`, `OPTIONS`.
- **Headers permitidos:** `Authorization`, `Content-Type`.
- **Credentials:** `false` (no cookies — auth es puramente por Bearer token en el header, no hay sesión/cookie).

Un cliente que corra en otro origin (ej. `http://localhost:5173` en dev)
va a ser bloqueado por CORS salvo que se agregue explícitamente a
`corsConfigurationSource()` en `SecurityConfig.java`.

---

## 8. Flujo completo (ejemplo con curl)

```bash
BASE=https://darmozsc.duckdns.org/auth

# 1. Registro
curl -s -X POST $BASE/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@darmoz.com","password":"Demo12345!"}'
# -> 201, guardar accessToken y refreshToken de la respuesta

# 2. Usar el access token contra un recurso protegido (de OTRO servicio,
#    que valida el JWT con la llave publica o llama a /verify)
curl -s https://darmozsc.duckdns.org/api/products \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. Verificar el token contra darmoz-auth (validacion remota)
curl -s -X POST $BASE/verify -H "Authorization: Bearer $ACCESS_TOKEN"

# 4. Refrescar cuando el access token esta por expirar (o ya expiro)
curl -s -X POST $BASE/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# -> 200, accessToken y refreshToken NUEVOS. Descartar los viejos.

# 5. Logout (revoca la sesion)
curl -s -X POST $BASE/logout \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# -> 204
```

---

## 9. Guía rápida para implementar un cliente

1. Guardar `accessToken` en memoria (no localStorage si se puede evitar —
   es un JWT legible, aunque no falsificable sin la llave privada).
   Guardar `refreshToken` en almacenamiento persistente seguro (httpOnly
   cookie si el cliente es un backend-for-frontend; storage seguro si es
   una app nativa).
2. En cada request a un recurso protegido, mandar
   `Authorization: Bearer <accessToken>`.
3. Si una request devuelve `401` y el token no expiró todavía (chequear
   `exp` del JWT decodificado localmente, sin verificar firma, solo para
   decidir si vale la pena reintentar), asumir que fue revocado
   (`/verify` para confirmar el motivo si hace falta) y forzar re-login.
4. Si expiró (`exp` pasado), llamar `POST /refresh` **una sola vez**
   (deduplicar llamadas concurrentes), reintentar la request original con
   el `accessToken` nuevo.
5. Si `/refresh` devuelve `401`, el usuario tiene que loguearse de nuevo —
   no hay forma de recuperar la sesión (podría ser un refresh token
   reusado/robado, o expirado).
6. En logout, mandar siempre el `accessToken` en el header además del
   `refreshToken` en el body, para que la revocación sea inmediata en
   ambos.

---

## 10. Configuración relevante (variables de entorno)

No es necesario para consumir la API, pero ayuda a entender el
comportamiento observado en cada entorno:

| Variable | Default | Efecto |
|---|---|---|
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `720` (12 min) | Vida del access token. |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `30` | Vida del refresh token. |
| `JWT_ISSUER` | `darmoz-auth` | Claim `iss` del JWT. |

Ver [`deploy/.env.example`](deploy/.env.example) para el resto de la
configuración de infraestructura (no relevante para un cliente de la API).
