-- auth_user_roles: user_id + role(text) -> user_id + role_id(FK auth_roles)
ALTER TABLE auth_user_roles ADD COLUMN role_id UUID REFERENCES auth_roles(id);
UPDATE auth_user_roles ur SET role_id = r.id
    FROM auth_roles r WHERE r.name = ur.role;
ALTER TABLE auth_user_roles ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE auth_user_roles DROP CONSTRAINT auth_user_roles_pkey;
ALTER TABLE auth_user_roles DROP COLUMN role;
ALTER TABLE auth_user_roles ADD PRIMARY KEY (user_id, role_id);

-- auth_role_permissions: role(text) -> role_id(FK auth_roles)
ALTER TABLE auth_role_permissions ADD COLUMN role_id UUID REFERENCES auth_roles(id);
UPDATE auth_role_permissions rp SET role_id = r.id
    FROM auth_roles r WHERE r.name = rp.role;
ALTER TABLE auth_role_permissions ALTER COLUMN role_id SET NOT NULL;
DROP INDEX IF EXISTS idx_auth_role_permissions_role;
DROP INDEX IF EXISTS uq_auth_role_permissions;
ALTER TABLE auth_role_permissions DROP COLUMN role;
CREATE INDEX idx_auth_role_permissions_role_id ON auth_role_permissions(role_id);
CREATE UNIQUE INDEX uq_auth_role_permissions ON auth_role_permissions(role_id, service, http_method, endpoint_pattern);
