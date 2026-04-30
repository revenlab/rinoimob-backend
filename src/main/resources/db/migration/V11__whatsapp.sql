CREATE TABLE whatsapp_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    instance_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE whatsapp_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    lead_id UUID REFERENCES leads(id) ON DELETE SET NULL,
    instance_id UUID NOT NULL REFERENCES whatsapp_instances(id) ON DELETE CASCADE,
    direction VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    sent_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    external_message_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wa_messages_lead ON whatsapp_messages(lead_id);
CREATE INDEX idx_wa_messages_tenant ON whatsapp_messages(tenant_id, created_at DESC);
CREATE INDEX idx_wa_instances_tenant ON whatsapp_instances(tenant_id);
