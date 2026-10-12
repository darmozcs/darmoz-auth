-- Aplicacion "Vivos" (tienda-admin + vivos-be). Id fijo y explicito, igual que la aplicacion
-- de sistema en V5: vivos-be y el frontend lo usan como API_ID / VIVOS_APPLICATION_ID y tiene
-- que ser el mismo en todo entorno.
--
-- unverified_login_limit = 0: los admins de tienda los crea el Super Admin de Vivos via
-- POST /apps/api/users con email_verified = FALSE y tienen que confirmar el codigo antes del
-- primer login (ver /verify-email/confirm-with-credentials).
INSERT INTO auth_applications (id, service_name, name, description, unverified_login_limit) VALUES
    ('22222222-2222-2222-2222-222222222222', 'vivos', 'Vivos',
        'Plataforma de vivos/tiendas (tienda-admin + vivos-be)', 0);

-- Roles de Vivos:
--   SUPER_ADMIN: rol de negocio en vivos-be (alta/gestion de tiendas).
--   APP_ADMIN:   habilita /apps/api/** en darmoz-auth, limitado a los usuarios de Vivos.
--   ADMIN:       administrador de una tienda.
INSERT INTO auth_roles (id, name, description, application_id) VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', 'Super Admin de Vivos: gestiona tiendas en vivos-be',
        '22222222-2222-2222-2222-222222222222'),
    (gen_random_uuid(), 'APP_ADMIN', 'Gestiona los usuarios de su propia aplicacion via /apps/api/**',
        '22222222-2222-2222-2222-222222222222'),
    (gen_random_uuid(), 'ADMIN', 'Administrador de una tienda de Vivos',
        '22222222-2222-2222-2222-222222222222');

-- Limite de intentos fallidos por codigo de verificacion: al llegar al maximo
-- (EmailVerificationProperties.maxFailedAttempts, default 5) el codigo se consume y hay que
-- pedir uno nuevo.
ALTER TABLE auth_email_verification_tokens ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
