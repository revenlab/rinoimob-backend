CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    action VARCHAR(255) NOT NULL,
    resource VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);

DO $$
BEGIN
    IF to_regclass('public.audit_log') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM audit_logs LIMIT 1)
    THEN
        INSERT INTO audit_logs (
            tenant_id,
            user_id,
            action,
            resource,
            resource_id,
            details,
            created_at
        )
        SELECT
            tenant_id::text,
            actor_id::text,
            action,
            resource_type,
            resource_id::text,
            changes::text,
            created_at
        FROM audit_log;
    END IF;
END $$;
