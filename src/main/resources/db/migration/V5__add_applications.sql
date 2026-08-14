-- Aplicaciones (tenants): cada Application agrupa un conjunto de usuarios y
-- roles propios. La aplicacion de sistema usa un id fijo y explicito (no
-- gen_random_uuid()) porque SuperAdminBootstrap.java lo referencia como
-- constante Java (SYSTEM_APPLICATION_ID) y tiene que ser igual en todo
-- entorno. La aplicacion de Laryon usa id autogenerado; despues de correr
-- esta migracion en produccion hay que consultarlo (SELECT id FROM
-- auth_applications WHERE name = 'Laryon') y hardcodearlo como API_ID en
-- uvc_selector/lib/auth_service.dart.
CREATE TABLE auth_applications (
    id UUID PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO auth_applications (id, service_name, name, description) VALUES
    ('11111111-1111-1111-1111-111111111111', 'darmoz-auth', 'darmoz-auth-admin',
        'Aplicacion interna: panel de administracion / usuarios SUPER de darmoz-auth');

INSERT INTO auth_applications (id, service_name, name, description) VALUES
    (gen_random_uuid(), 'laryon-apk', 'Laryon',
        'App movil laryon (uvc_selector), consumidor productivo existente');

-- auth_roles: name (unique global) -> application_id + name (unique por app)
ALTER TABLE auth_roles ADD COLUMN application_id UUID REFERENCES auth_applications(id);

UPDATE auth_roles SET application_id = '11111111-1111-1111-1111-111111111111'
    WHERE name = 'SUPER';

-- Cualquier otro rol existente (USER, ADMIN, o roles custom creados desde el
-- panel admin desde que ese feature se desplego) pertenece hoy a la unica
-- aplicacion consumidora productiva real.
UPDATE auth_roles SET application_id = (SELECT id FROM auth_applications WHERE name = 'Laryon')
    WHERE application_id IS NULL;

ALTER TABLE auth_roles ALTER COLUMN application_id SET NOT NULL;
ALTER TABLE auth_roles DROP CONSTRAINT auth_roles_name_key;
CREATE UNIQUE INDEX uq_auth_roles ON auth_roles(application_id, name);
CREATE INDEX idx_auth_roles_application_id ON auth_roles(application_id);

-- auth_users: email (unique global) -> application_id + email (unique por app)
ALTER TABLE auth_users ADD COLUMN application_id UUID REFERENCES auth_applications(id);

-- Usuarios con el rol SUPER asignado (via auth_user_roles) pertenecen a la
-- aplicacion de sistema.
UPDATE auth_users u SET application_id = '11111111-1111-1111-1111-111111111111'
    FROM auth_user_roles ur
    JOIN auth_roles r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.name = 'SUPER';

-- Todos los demas usuarios existentes pertenecen a la unica aplicacion
-- consumidora productiva actual.
UPDATE auth_users SET application_id = (SELECT id FROM auth_applications WHERE name = 'Laryon')
    WHERE application_id IS NULL;

ALTER TABLE auth_users ALTER COLUMN application_id SET NOT NULL;
ALTER TABLE auth_users DROP CONSTRAINT auth_users_email_key;
CREATE UNIQUE INDEX uq_auth_users ON auth_users(application_id, email);
CREATE INDEX idx_auth_users_application_id ON auth_users(application_id);
