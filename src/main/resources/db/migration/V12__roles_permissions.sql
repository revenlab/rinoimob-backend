-- tenant_roles: roles customizáveis por tenant
CREATE TABLE IF NOT EXISTS tenant_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- role_permissions: permissões de cada role
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES tenant_roles(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    UNIQUE (role_id, permission)
);

-- Adicionar colunas na tabela users
ALTER TABLE users ADD COLUMN IF NOT EXISTS system_role VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_role_id UUID REFERENCES tenant_roles(id);

-- Migrar TENANT_OWNER e TENANT_ADMIN para system_role
UPDATE users SET system_role = role WHERE role IN ('TENANT_OWNER', 'TENANT_ADMIN');

-- Tornar role nullable para usuários com tenant_role
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT NULL;
-- Limpar role para quem já tem system_role
UPDATE users SET role = NULL WHERE system_role IS NOT NULL;
