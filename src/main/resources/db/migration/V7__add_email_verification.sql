-- Verificacion de email por codigo numerico corto (graduada por aplicacion, no global).
--
-- unverified_login_limit (auth_applications): cantidad de logins que se permiten a un usuario
-- todavia no verificado antes de que /login lo bloquee con EMAIL_NOT_VERIFIED. Default 0 =
-- sin logins de gracia (el primer /login post-registro ya queda bloqueado hasta verificar).
-- Cada tenant puede subir el limite desde el panel admin (AdminApplicationService).
ALTER TABLE auth_applications ADD COLUMN unverified_login_limit INT NOT NULL DEFAULT 0;

-- email_verified / unverified_login_count (auth_users): estado de verificacion por usuario.
-- Se agrega con default FALSE / 0 y despues se hace backfill explicito de las cuentas
-- EXISTENTES a TRUE: el feature aplica solo a cuentas creadas DESPUES de este deploy, una
-- cuenta activa hoy jamas debe quedar bloqueada retroactivamente por un email que nunca se
-- le pidio verificar. Mismo criterio de backfill-explicito que V5 usa para application_id.
ALTER TABLE auth_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_users ADD COLUMN unverified_login_count INT NOT NULL DEFAULT 0;

UPDATE auth_users SET email_verified = TRUE;

-- auth_email_verification_tokens: mismo patron que auth_refresh_tokens (solo se guarda el
-- hash SHA-256 del codigo, nunca el valor crudo). A diferencia del refresh token, el valor
-- generado es un codigo numerico corto de 6 digitos, porque el usuario lo tipea a mano.
--
-- A diferencia de auth_refresh_tokens.user_id (columna cruda sin FK, ver V6), aca SI hay FK
-- con ON DELETE CASCADE: estos codigos son de vida muy corta y sin valor de auditoria una vez
-- que el usuario deja de existir, asi que no hay motivo para preservarlos huerfanos.
CREATE TABLE auth_email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_email_verification_tokens_user_id ON auth_email_verification_tokens(user_id);
CREATE INDEX idx_auth_email_verification_tokens_expires_at ON auth_email_verification_tokens(expires_at);

-- auth_audit_log.action se creo VARCHAR(20) en V6, dimensionada para las 6 acciones de ese
-- momento (la mas larga, "REGISTER", tiene 8 caracteres). Las dos acciones nuevas
-- (REQUEST_EMAIL_VERIFICATION / CONFIRM_EMAIL_VERIFICATION) no entran en 20 caracteres; se
-- ensancha la columna con margen para futuras acciones en vez de recortar los nombres a algo
-- menos legible.
ALTER TABLE auth_audit_log ALTER COLUMN action TYPE VARCHAR(40);
