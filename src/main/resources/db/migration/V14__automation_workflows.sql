-- automation_workflows table
CREATE TABLE IF NOT EXISTS automation_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    workflow_config JSONB NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_by_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

-- automation_executions table (audit log)
CREATE TABLE IF NOT EXISTS automation_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES automation_workflows(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    trigger_event VARCHAR(50),
    trigger_data JSONB,
    execution_path JSONB,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    result_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_automation_workflows_tenant ON automation_workflows(tenant_id);
CREATE INDEX idx_automation_workflows_active ON automation_workflows(is_active);
CREATE INDEX idx_automation_executions_workflow ON automation_executions(workflow_id);
CREATE INDEX idx_automation_executions_status ON automation_executions(status);
CREATE INDEX idx_automation_executions_tenant ON automation_executions(tenant_id);
CREATE INDEX idx_automation_executions_created ON automation_executions(created_at DESC);
