-- Tabela de permissões de suporte por usuário
CREATE TABLE support_user_permissions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, permission)
);

CREATE INDEX idx_support_user_permissions_user_id ON support_user_permissions(user_id);
