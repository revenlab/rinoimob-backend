-- Create in_app_notifications table
CREATE TABLE IF NOT EXISTS in_app_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'INFO' CHECK (type IN ('INFO', 'WARNING', 'ERROR', 'SUCCESS')),
    is_read BOOLEAN NOT NULL DEFAULT false,
    read_at TIMESTAMP,
    metadata JSONB,
    action_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_tenant_id ON in_app_notifications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_recipient_id ON in_app_notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_created_at ON in_app_notifications(created_at);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_is_read ON in_app_notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_tenant_recipient ON in_app_notifications(tenant_id, recipient_id);
CREATE INDEX IF NOT EXISTS idx_in_app_notifications_tenant_recipient_read ON in_app_notifications(tenant_id, recipient_id, is_read);
