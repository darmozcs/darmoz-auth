CREATE TABLE auth_roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO auth_roles (id, name, description) VALUES
    (gen_random_uuid(), 'USER', 'Rol base asignado a todo usuario registrado'),
    (gen_random_uuid(), 'ADMIN', 'Rol administrativo de aplicaciones consumidoras'),
    (gen_random_uuid(), 'SUPER', 'Acceso al panel de administracion de darmoz-auth (usuarios, roles, permisos)');
