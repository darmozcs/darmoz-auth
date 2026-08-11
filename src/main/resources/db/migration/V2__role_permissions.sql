CREATE TABLE auth_role_permissions (
    id UUID PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    service VARCHAR(100) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_pattern VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_role_permissions_role ON auth_role_permissions(role);
CREATE UNIQUE INDEX uq_auth_role_permissions ON auth_role_permissions(role, service, http_method, endpoint_pattern);
