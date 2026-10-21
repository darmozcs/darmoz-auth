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

**Swagger:** `https://darmozsc.duckdns.org/auth/swagger-ui.html` (UI para probar) y `/auth/v3/api-docs` (OpenAPI JSON para Postman o generar clientes). Se genera desde el código; en **Authorize** se cargan el `API_ID` y el Bearer. Se apaga con `API_DOCS_ENABLED=false`.

**Multi-tenant desde esta versión:** todo consumidor (frontend, app móvil,
otro backend) representa una **Aplicación** dentro de darmoz-auth, y tiene
que mandar un header `API_ID` con el UUID de esa aplicación en **todos** los
endpoints de la sección 6. Ver sección 3 para el detalle completo.

---

## 2. Modelo de autenticación

Dos tipos de token, devueltos juntos en cada login/registro/refresh:

- **`accessToken`** — JWT firmado con RSA (RS256), de vida corta (default
  **720 segundos / 12 min**, configurable). Va en el header
  `Authorization: Bearer <accessToken>` de cada request a un recurso
  protegido. Es **autocontenido**: incluye el user id, email, roles y
  permisos ya resueltos, así que un servicio consumidor puede leerlo sin
  necesariamente llamar de vuelta a darmoz-auth (ver sección 6).
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

## 3. Aplicaciones y el header `API_ID`

darmoz-auth es un servicio de auth **compartido entre varias aplicaciones
consumidoras** (tabla `auth_applications`: id, nombre de servicio, nombre,
descripción). Cada `Usuario` y cada `Rol` pertenecen a una única Aplicación
— el mismo email puede existir como cuentas completamente independientes en
dos aplicaciones distintas.

**Cómo conseguir el `API_ID`:** un SUPER admin crea la aplicación desde el
panel (`/auth/admin` → pestaña "Aplicaciones") y comparte el UUID generado
con el equipo que integra el cliente. No hay forma de auto-registrar una
aplicación nueva vía API pública — es un paso manual de admin.

**Uso:** todos los endpoints de la sección 6 (`register`, `login`,
`refresh`, `verify`, `logout`, `disable`, `password`, `verify-email/*`)
requieren el header:

```
API_ID: <uuid-de-tu-aplicacion>
```

El servidor valida dos cosas en cada llamada:
1. Que el `API_ID` corresponda a una aplicación existente (si no, `404`).
2. Que el usuario/token involucrado pertenezca a **esa misma** aplicación
   (si no, `403` con `message` describiendo el mismatch — excepto en
   `/verify`, que nunca tira error HTTP: ver su sección para el detalle).

Si el header falta directamente, la respuesta es `400` (ver sección 7,
formato de error genérico).

**Roles y permisos también son por-aplicación:** un rol como `USER` en la
Aplicación A es una fila completamente distinta de un rol `USER` en la
Aplicación B — mismo nombre, sin relación entre sí. Los permisos
(`auth_role_permissions`) heredan la aplicación de su rol.

---

## 4. Estructura del Access Token (JWT)

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
  "applicationId": "22222222-2222-2222-2222-222222222222",
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
| `roles` | Array de roles (`USER`, `ADMIN`; ver sección 5). |
| `permissions` | Lista de permisos resueltos **al momento de emitir el token** (ver sección 5). Cambios de permisos en la base no afectan tokens ya emitidos hasta el próximo `login`/`refresh`. |
| `applicationId` | UUID de la Aplicación a la que pertenece el usuario (ver sección 3). Tokens emitidos antes de que este claim existiera no lo tienen. |
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

## 5. Roles y permisos

- **Roles**: dinámicos, gestionados desde el panel admin, y **scoped a una
  Aplicación** (ver sección 3) — el nombre de un rol es único dentro de su
  aplicación, no globalmente. Toda Aplicación nueva se crea con un rol base
  `USER`; todo usuario nuevo (`/register`) se crea con ese `USER` de su
  aplicación únicamente — no hay forma de auto-asignarse otro rol vía API
  pública.
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
  embebida en el JWT como el claim `permissions` (ver sección 4). Un
  servicio consumidor que reciba el token puede autorizar la request
  localmente comparando `(service, method, path)` contra su propio nombre
  de servicio + el método/path de la request entrante, **sin llamar a
  darmoz-auth**.
- Alta de roles y de filas en `auth_role_permissions` es vía el panel admin
  (`/auth/admin`, pestañas "Roles" y "Permisos"), no hay endpoint público.

---

## 6. Endpoints

Todos requieren el header `API_ID` (sección 3), además de lo que se detalla
en cada uno.

### `POST /auth/register`

Crea un usuario nuevo con el rol `USER` **de la aplicación indicada en
`API_ID`** y devuelve tokens (equivalente a registrarse + loguearse en un
solo paso). Si el email ya existe en esa aplicación responde `409`, **también
si la cuenta está deshabilitada** (`enabled=false`): `/register` nunca
reactiva cuentas (antes lo hacía; se quitó porque permitía revertir una baja
hecha por un admin).

**Headers:** `API_ID: <uuid>` (requerido).

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
| `400` | falta el header `API_ID`, o email inválido / password fuera de rango / campos vacíos | `{"message": "email: must be a well-formed email address; password: size must be between 8 and 100", ...}` |
| `404` | `API_ID` no corresponde a ninguna aplicación (o no es un UUID válido) | `{"message": "Aplicacion no encontrada", ...}` |
| `409` | el email ya está registrado en esa aplicación (habilitado o deshabilitado) | `{"message": "Ya existe una cuenta con ese email", ...}` |

---

### `POST /auth/login`

**Headers:** `API_ID: <uuid>` (requerido).

**Body:**
```json
{ "email": "usuario@ejemplo.com", "password": "unaPasswordSegura123" }
```

`email` acepta también **solo la parte anterior al `@`** (ej. `"usuario"`). Esto vale únicamente para `/login`: `register`, los endpoints de verificación de email y los de administración siguen exigiendo el email completo.
- Se buscan los usuarios de la aplicación cuyo email empieza con `<valor>@`. Los comodines `%` y `_` se toman literalmente.
- Si varios comparten esa parte (ej. `ana@gmail.com` y `ana@hotmail.com`), entra el primero, en orden de alta, que esté habilitado y cuya password coincida.
- Si ninguno coincide, se devuelve el mismo `401` que con un email incorrecto.

**200 OK:** mismo shape que `register` (`AuthResponse`, ver arriba).

**Errores:**
| Status | Cuándo | Body |
|---|---|---|
| `400` | falta `API_ID`, o campos vacíos | validación |
| `404` | `API_ID` no corresponde a ninguna aplicación | `{"message": "Aplicacion no encontrada", ...}` |
| `401` | email no existe **en esa aplicación**, password incorrecta, o cuenta deshabilitada (`enabled=false`) | `{"message": "Email o password invalidos", ...}` |
| `403` | el email del usuario todavía no está verificado y ya se agotó la cantidad de logins de gracia de la aplicación (`unverified_login_limit`, default `0`) | `{"error": "EMAIL_NOT_VERIFIED", "message": "Debes verificar tu email antes de iniciar sesion", ...}` |

Nota: el mensaje es idéntico para "no existe", "password mal" y "cuenta
deshabilitada" — deliberado, para no filtrar si un email está registrado. Un
email registrado en **otra** aplicación tampoco se distingue de "no existe".

El caso `EMAIL_NOT_VERIFIED` es el único error de este endpoint pensado para
que el cliente lo distinga programáticamente por el campo `error` (no solo
por el status) — ver `POST /auth/verify-email/request` más abajo para
iniciar la verificación.

---

### `POST /auth/refresh`

Rota el refresh token (revoca el usado, emite uno nuevo) y emite un access
token nuevo con roles/permisos **recalculados en ese momento** (útil si
cambiaron los roles del usuario desde el login original).

**Headers:** `API_ID: <uuid>` (requerido).

**Body:**
```json
{ "refreshToken": "8f3a2b1c4d5e4f6a8b7c9d0e1f2a3b4c..." }
```

**200 OK:** `AuthResponse` completo, con `accessToken` y `refreshToken`
**nuevos**. El `refreshToken` recibido queda inválido inmediatamente.

**Errores:**
| Status | Body `message` | Causa |
|---|---|---|
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `403` | `El refresh token no pertenece a la aplicacion indicada` | el refresh token es válido pero es de un usuario de **otra** aplicación — el token rotado se revoca automáticamente antes de responder, así que no queda ninguna credencial usable filtrada |
| `401` | `Refresh token invalido` | no existe / no corresponde a ningún hash en la base |
| `401` | `Refresh token expirado` | pasó el TTL (default 30 días) |
| `401` | `Refresh token reusado; se revocaron todas las sesiones` | el token ya había sido usado antes → se revocaron **todas** las sesiones activas del usuario, hay que loguear de nuevo |
| `401` | `Usuario deshabilitado` | el usuario fue deshabilitado (por un admin o por `/disable`); se revocan todas sus sesiones |

---

### `POST /auth/logout`

Revoca el refresh token (body) y, si se manda el access token, también lo
agrega a la blocklist de revocados (por `jti`) para que `/verify` y
cualquier chequeo de revocación lo vean como inválido de inmediato.

**Headers:**
- `API_ID: <uuid>` — requerido (solo se valida que la aplicación exista;
  **no** se compara contra el usuario dueño del token, a diferencia del
  resto de los endpoints — ver nota abajo).
- `Authorization: Bearer <accessToken>` — **opcional pero recomendado**. Si
  no se manda, solo se revoca el refresh token; el access token sigue
  siendo válido (en validación local) hasta que expira solo.

**Body:**
```json
{ "refreshToken": "8f3a2b1c4d5e4f6a8b7c9d0e1f2a3b4c..." }
```

**204 No Content** — siempre, sea el token válido, inválido o inexistente
(operación idempotente, no filtra información).

**Errores:** `400` si `refreshToken` viene vacío/ausente o si falta
`API_ID`; `404` si `API_ID` no corresponde a ninguna aplicación.

> **Nota sobre `API_ID` en logout:** a diferencia de los demás endpoints,
> acá no se rechaza un mismatch de aplicación — es una decisión deliberada
> (revocar una sesión de la que ya se posee el secreto no representa riesgo
> cross-tenant, y el `accessToken` es opcional en este endpoint).

---

### `POST /auth/verify`

Valida un access token del lado del servidor: firma, expiración, si está en
la blocklist de revocados, **y si pertenece a la aplicación indicada en
`API_ID`**. Pensado para que otro servicio delegue la validación completa
(incluida revocación) en darmoz-auth en vez de validar localmente.

**Headers:**
- `API_ID: <uuid>` — requerido. Si no corresponde a ninguna aplicación,
  responde `404` (a diferencia del resto de los chequeos de este endpoint,
  que nunca tiran error HTTP — ver abajo).
- `Authorization: Bearer <accessToken>` — opcional; si falta, responde
  `valid: false, reason: "missing_token"`.

Sin body.

**200 OK** (asumiendo que `API_ID` es válido — este endpoint **nunca**
devuelve 401/403 por un problema del token en sí; el resultado va en el
body):

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
`malformed`, `revoked`, `application_mismatch` (el token es válido pero es
de un usuario de otra aplicación, distinta de la indicada en `API_ID`),
`disabled` (el token es válido pero el usuario fue deshabilitado o eliminado
después de emitirlo).

---

### `POST /auth/disable`

Deshabilita al usuario **autenticado por el propio access token** (no
recibe un id en el path ni en el body): pone `enabled=false`, revoca todos
sus refresh tokens activos y bloquea el access token actual. Pensado como
"cerrar sesión" definitivo/borrado de cuenta desde el propio cliente — no
hay vuelta atrás desde la API pública: `/register` con el mismo email responde
`409`; solo un admin puede volver a habilitar la cuenta.

**Headers:**
- `API_ID: <uuid>` — requerido.
- `Authorization: Bearer <accessToken>` — **requerido** (a diferencia de
  `/logout`, acá no es opcional).

Sin body.

**204 No Content** en éxito.

**Errores:**
| Status | Body `message` | Causa |
|---|---|---|
| `400` | — | falta `API_ID` o `Authorization` |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `403` | `El access token no pertenece a la aplicacion indicada` | el token es de un usuario de otra aplicación |
| `404` | `Usuario no encontrado` | el usuario del token ya no existe (caso raro) |

---

### `POST /auth/verify-email/request`

Genera un código numérico de 6 dígitos, invalida cualquier código activo
previo del usuario, lo persiste (solo el hash) y lo envía por email vía
darmoz-mail (`accion: "VERIFY"`). Pensado para llamarse justo después de
`/register`, y para reenvíos ("no me llegó el código").

**Headers:**
- `API_ID: <uuid>` — requerido.
- `Authorization: Bearer <accessToken>` — **requerido**.

Sin body.

**204 No Content** en éxito (el código se manda por email, no se devuelve
en la respuesta).

**Errores:**
| Status | Body `message`/`error` | Causa |
|---|---|---|
| `400` | — | falta `API_ID` o `Authorization` |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `403` | `El access token no pertenece a la aplicacion indicada` | el token es de un usuario de otra aplicación |
| `404` | `Usuario no encontrado` | el usuario del token ya no existe |
| `409` | `El email ya esta verificado` | no hace falta pedir un código nuevo |
| `429` | `error: "TOO_MANY_REQUESTS"` | ya se envió un código hace menos de 60 s (throttle de reenvío, ver nota abajo) |
| `502` | — | falló el envío hacia darmoz-mail (red caída o respuesta de error) |

> **Throttle de reenvío** (aplica a todos los endpoints que envían código:
> `/verify-email/request`, `/verify-email/resend-with-credentials` y
> `/apps/api/users/{id}/verification-email`): si el último código del
> usuario se emitió hace menos de `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS`
> (default 60), responde `429` con `"error": "TOO_MANY_REQUESTS"`. El cliente
> debería mostrar un cooldown equivalente en la UI.

---

### `POST /auth/verify-email/confirm`

Confirma el código recibido por email. Si es válido (no expiró, no fue
consumido ya), marca al usuario como `email_verified=true` y resetea su
contador de logins sin verificar.

**Headers:**
- `API_ID: <uuid>` — requerido.
- `Authorization: Bearer <accessToken>` — **requerido**.

**Body:**
```json
{ "code": "482913" }
```

**204 No Content** en éxito.

**Errores:**
| Status | Body `message`/`error` | Causa |
|---|---|---|
| `400` | validación | `code` ausente o no son 6 dígitos |
| `400` | `Codigo invalido o expirado` | no coincide con el código activo, o ya venció (TTL configurable, default 15 min), o se agotaron los intentos |
| `400` | `Codigo invalido; se agotaron los intentos, solicita un codigo nuevo` | este fue el 5º intento fallido con el mismo código |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `403` | `El access token no pertenece a la aplicacion indicada` | el token es de un usuario de otra aplicación |
| `409` | `El email ya esta verificado` | ya se había confirmado antes |

> **Límite de intentos** (aplica también a `/verify-email/confirm-with-credentials`):
> cada código incorrecto suma un intento fallido al código activo del
> usuario. Al llegar a `EMAIL_VERIFICATION_MAX_FAILED_ATTEMPTS` (default 5)
> el código se consume: cualquier confirmación posterior (incluso con el
> código correcto) responde `400` hasta pedir un código nuevo.

---

### `POST /auth/verify-email/confirm-with-credentials`

Variante **sin Bearer** de `/verify-email/confirm`, para usuarios que todavía
no pueden obtener un token (por ejemplo, un admin creado vía
`POST /apps/api/users` en una aplicación con `unverified_login_limit=0`:
su `/login` responde `403 EMAIL_NOT_VERIFIED`). Prueba las credenciales en
el body, confirma el código, marca `email_verified=true` y **deja al usuario
logueado**: responde lo mismo que `/login`.

Pedir la password evita que un tercero que solo conoce el email confirme o
fuerce intentos sobre el código.

**Headers:** `API_ID: <uuid>` (requerido). Sin `Authorization`.

**Body:**
```json
{ "email": "admin@tienda.com", "password": "unaPasswordSegura123", "code": "482913" }
```
Validación: los tres campos requeridos; `code` de 6 dígitos.

**200 OK:** `AuthResponse` (mismo shape que `/login`), con `emailVerified: true`.

**Errores:**
| Status | Body `message`/`error` | Causa |
|---|---|---|
| `400` | validación | campos vacíos o `code` que no son 6 dígitos |
| `400` | `Codigo invalido o expirado` / `...se agotaron los intentos...` | código incorrecto, vencido o agotado (ver límite de intentos arriba) |
| `401` | `Email o password invalidos` | email inexistente en esa aplicación, password incorrecta o cuenta deshabilitada |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `409` | `El email ya esta verificado` | ya se había confirmado antes (usar `/login`) |

Se audita como `CONFIRM_EMAIL_VERIFICATION` (y además `LOGIN` si sale bien).

---

### `POST /auth/verify-email/resend-with-credentials`

Variante **sin Bearer** de `/verify-email/request`: prueba las credenciales y
envía un código nuevo (invalida el anterior).

**Headers:** `API_ID: <uuid>` (requerido). Sin `Authorization`.

**Body:**
```json
{ "email": "admin@tienda.com", "password": "unaPasswordSegura123" }
```

**204 No Content** en éxito.

**Errores:**
| Status | Body `message`/`error` | Causa |
|---|---|---|
| `400` | validación | campos vacíos |
| `401` | `Email o password invalidos` | credenciales inválidas o cuenta deshabilitada |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |
| `409` | `El email ya esta verificado` | no hace falta un código nuevo |
| `429` | `error: "TOO_MANY_REQUESTS"` | throttle de reenvío (60 s desde el último código) |
| `502` | — | falló el envío hacia darmoz-mail |

Se audita como `REQUEST_EMAIL_VERIFICATION`.

---

### `POST /auth/password`

Cambia la password del usuario autenticado por el access token. Si sale
bien: actualiza el hash, **revoca todos sus refresh tokens** (todas las
sesiones, en todos los dispositivos) y bloquea el access token actual. El
cliente tiene que volver a loguearse con la password nueva.

**Headers:**
- `API_ID: <uuid>` — requerido; tiene que coincidir con la aplicación del token.
- `Authorization: Bearer <accessToken>` — **requerido**.

**Body:**
```json
{ "currentPassword": "laPasswordActual", "newPassword": "unaPasswordNueva123" }
```
Validación: ambos requeridos; `newPassword` entre 8 y 100 caracteres.

**204 No Content** en éxito.

**Errores:**
| Status | Body `message`/`error` | Causa |
|---|---|---|
| `400` | validación | campos vacíos / `newPassword` fuera de rango, o falta `API_ID` |
| `400` | `error: "INVALID_CURRENT_PASSWORD"`, `message: "La contrasena actual no es correcta"` | `currentPassword` no coincide |
| `401` | — | sin token, token inválido/expirado/revocado, o usuario deshabilitado |
| `403` | `El access token no pertenece a la aplicacion indicada` | `API_ID` distinto de la aplicación del token |
| `404` | `Aplicacion no encontrada` | `API_ID` inválido/inexistente |

Se audita como `CHANGE_PASSWORD`.

---

### Administración de usuarios de la propia aplicación — `/auth/apps/api/users`

Pensado para que el backend de una aplicación (por ejemplo vivos-be) dé de
alta y gestione a sus propios usuarios reenviando el Bearer de un admin de
esa aplicación — sin credenciales globales.

**Requisitos:** `Authorization: Bearer <accessToken>` de un usuario con el
rol **`APP_ADMIN`** (sin token → `401`; sin el rol → `403`). `API_ID` no es
necesario: **todas las operaciones quedan limitadas a la aplicación del
token** (claim `applicationId`). Un usuario de otra aplicación es
indistinguible de uno inexistente: `404 "Usuario no encontrado"`.

Respuesta de usuario (`AdminUserResponse`, igual que en `/admin/api/users`):
```json
{
  "id": "0f3a2b1c-4d5e-4f6a-8b7c-9d0e1f2a3b4c",
  "email": "admin@tienda.com",
  "enabled": true,
  "emailVerified": false,
  "unverifiedLoginCount": 0,
  "applicationId": "22222222-2222-2222-2222-222222222222",
  "applicationName": "Vivos",
  "roles": ["ADMIN"],
  "createdAt": "2026-10-11T13:00:00Z",
  "updatedAt": "2026-10-11T13:00:00Z"
}
```

| Método y ruta | Body | Respuesta | Notas |
|---|---|---|---|
| `GET /apps/api/users` | — | `200` `AdminUserResponse[]` | usuarios de la aplicación del token |
| `GET /apps/api/users/{id}` | — | `200` `AdminUserResponse` | |
| `POST /apps/api/users` | `{"email", "password" (8-100), "roles": [..] (no vacío)}` | `201` `AdminUserResponse` | ver alta abajo |
| `PATCH /apps/api/users/{id}` | `{"enabled"?: bool, "password"?: string (8-100)}` | `200` `AdminUserResponse` | update parcial; deshabilitar o cambiar password revoca todos sus refresh tokens |
| `DELETE /apps/api/users/{id}` | — | `204` | revoca sus refresh tokens y borra el usuario |
| `POST /apps/api/users/{id}/verification-email` | — | `204` | reenvía el código de verificación (throttle 60 s) |

**Alta (`POST`):** los roles se resuelven **dentro de la aplicación del
token**. El usuario se crea con `emailVerified=false` y, en la misma
transacción, se le envía el código de verificación por darmoz-mail. Si el
envío falla (`502`) se hace rollback: el usuario **no** queda creado. El
usuario confirma con `/verify-email/confirm-with-credentials`.

**Reglas y errores:**
| Status | Causa |
|---|---|
| `400` | validación del body |
| `404` | `Usuario no encontrado` (inexistente o de otra aplicación) / `Rol no encontrado: X` (el rol no existe en la aplicación del token) |
| `409` | `Ya existe una cuenta con ese email` |
| `409` | `No se puede asignar el rol APP_ADMIN` / `SUPER` (evita escalada de privilegios) |
| `409` | `No podes modificar ni eliminar tu propio usuario` (`PATCH`/`DELETE` sobre el id del token) |
| `409` | `No se puede modificar ni eliminar a otro administrador de la aplicacion` (`PATCH`/`DELETE` sobre un usuario con `APP_ADMIN`) |
| `409` | `El email ya esta verificado` (`verification-email` sobre un usuario ya verificado) |
| `429` | `TOO_MANY_REQUESTS` (`verification-email` antes de 60 s del último código) |
| `502` | falló el envío del código a darmoz-mail (alta o reenvío) |

**Aplicación Vivos** (sembrada por `V8__add_vivos_application.sql`): id fijo
`22222222-2222-2222-2222-222222222222`, `unverified_login_limit=0`, roles
`SUPER_ADMIN`, `APP_ADMIN` y `ADMIN`. El primer Super Admin de Vivos lo crea
un `SUPER` global con `POST /auth/admin/api/users`
(`{"email", "password", "applicationId": "22222222-...", "roles": ["SUPER_ADMIN", "APP_ADMIN"]}`).

> `/admin/api/**` (panel global) exige rol `SUPER` **y** que el token sea de
> la aplicación de sistema (`11111111-1111-1111-1111-111111111111`).

---

## 7. Formato de error genérico

Todos los errores (`400`, `403`, `404`, `409`, `429`, etc.) que no sean de
`/verify` (que siempre devuelve `200`) usan este shape:

```json
{
  "timestamp": "2026-08-11T22:57:19.298Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Email o password invalidos"
}
```

En general `error` es el reason phrase HTTP. Excepciones con un código
estable pensado para que el cliente branchee:

| `error` | Status | Dónde |
|---|---|---|
| `EMAIL_NOT_VERIFIED` | `403` | `/login` |
| `INVALID_CURRENT_PASSWORD` | `400` | `/password` |
| `TOO_MANY_REQUESTS` | `429` | endpoints que envían código de verificación |

---

## 8. CORS

Configurado en `SecurityConfig`:
- **Origins permitidos:** variable de entorno `CORS_ALLOWED_ORIGINS` (propiedad
  `cors.allowed-origins`), lista separada por comas. Default:
  `https://darmozsc.duckdns.org`. No hay wildcard. En dev, por ejemplo:
  `CORS_ALLOWED_ORIGINS=https://darmozsc.duckdns.org,http://localhost:5173`.
- **Métodos:** `GET`, `POST`, `OPTIONS` en la API pública (incluye `/password`
  y `/verify-email/*`, todos `POST`); además `PATCH` y `DELETE` en
  `/apps/api/**`; y `PUT`/`PATCH`/`DELETE` en `/admin/api/**`.
- **Headers permitidos:** `Authorization`, `Content-Type`, `API_ID`.
- **Credentials:** `false` (no cookies — auth es puramente por Bearer token en el header, no hay sesión/cookie).

---

## 9. Flujo completo (ejemplo con curl)

```bash
BASE=https://darmozsc.duckdns.org/auth
API_ID=22222222-2222-2222-2222-222222222222   # UUID de tu Aplicacion (seccion 3)

# 1. Registro
curl -s -X POST $BASE/register \
  -H 'Content-Type: application/json' -H "API_ID: $API_ID" \
  -d '{"email":"demo@darmoz.com","password":"Demo12345!"}'
# -> 201, guardar accessToken y refreshToken de la respuesta

# 2. Usar el access token contra un recurso protegido (de OTRO servicio,
#    que valida el JWT con la llave publica o llama a /verify)
curl -s https://darmozsc.duckdns.org/api/products \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. Verificar el token contra darmoz-auth (validacion remota)
curl -s -X POST $BASE/verify -H "Authorization: Bearer $ACCESS_TOKEN" -H "API_ID: $API_ID"

# 4. Refrescar cuando el access token esta por expirar (o ya expiro)
curl -s -X POST $BASE/refresh \
  -H 'Content-Type: application/json' -H "API_ID: $API_ID" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# -> 200, accessToken y refreshToken NUEVOS. Descartar los viejos.

# 5. Logout (revoca la sesion)
curl -s -X POST $BASE/logout \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' -H "API_ID: $API_ID" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# -> 204

# 6. Deshabilitar la cuenta (borrado logico; solo un admin puede volver a
#    habilitarla)
curl -s -X POST $BASE/disable \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H "API_ID: $API_ID"
# -> 204
```

---

## 10. Guía rápida para implementar un cliente

1. Guardar `accessToken` en memoria (no localStorage si se puede evitar —
   es un JWT legible, aunque no falsificable sin la llave privada).
   Guardar `refreshToken` en almacenamiento persistente seguro (httpOnly
   cookie si el cliente es un backend-for-frontend; storage seguro si es
   una app nativa). Guardar el `API_ID` de tu aplicación como constante —
   no cambia entre requests.
2. En cada llamada a un endpoint de la sección 6, mandar `API_ID`. En cada
   request a un recurso protegido, mandar además
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
   reusado/robado, o expirado). Si devuelve `403`, es un error de
   integración (el refresh token es de otra aplicación) — revisar el
   `API_ID` configurado.
6. En logout, mandar siempre el `accessToken` en el header además del
   `refreshToken` en el body, para que la revocación sea inmediata en
   ambos.

---

## 11. Configuración relevante (variables de entorno)

No es necesario para consumir la API, pero ayuda a entender el
comportamiento observado en cada entorno:

| Variable | Default | Efecto |
|---|---|---|
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `720` (12 min) | Vida del access token. |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `30` | Vida del refresh token. |
| `JWT_ISSUER` | `darmoz-auth` | Claim `iss` del JWT. |
| `EMAIL_VERIFICATION_CODE_TTL_MINUTES` | `15` | Vigencia del código de `/verify-email/request`. |
| `EMAIL_VERIFICATION_MAX_FAILED_ATTEMPTS` | `5` | Intentos fallidos por código antes de consumirlo. |
| `EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS` | `60` | Tiempo mínimo entre dos envíos de código al mismo usuario (si no, `429`). |
| `CORS_ALLOWED_ORIGINS` | `https://darmozsc.duckdns.org` | Origins permitidos por CORS, separados por coma. |
| `DARMOZ_MAIL_BASE_URL` | `http://localhost:8081/darmoz-mail` | Base URL de darmoz-mail (incluye context-path). |
| `DARMOZ_MAIL_CLIENT_ID` | (uuid fijo del client application de darmoz-auth en darmoz-mail) | Header `X-Client-Id` al llamar `POST /emails`. |

Ver [`deploy/.env.example`](deploy/.env.example) para el resto de la
configuración de infraestructura (no relevante para un cliente de la API).
