CREATE TABLE task_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,  -- NULL = system default
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20) NOT NULL DEFAULT '#6366f1',
    icon VARCHAR(50),          -- icon name/identifier for frontend
    position INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- System defaults (tenant_id = NULL)
INSERT INTO task_types (id, tenant_id, name, color, icon, position) VALUES
  (gen_random_uuid(), NULL, 'Visita',     '#8b5cf6', 'home',      1),
  (gen_random_uuid(), NULL, 'Ligação',    '#3b82f6', 'phone',     2),
  (gen_random_uuid(), NULL, 'Reunião',    '#f59e0b', 'users',     3),
  (gen_random_uuid(), NULL, 'Follow-up',  '#10b981', 'refresh',   4),
  (gen_random_uuid(), NULL, 'Email',      '#6366f1', 'mail',      5),
  (gen_random_uuid(), NULL, 'Outro',      '#64748b', 'dots',      6);

-- Add task_type_id to tasks (nullable for backward compat)
ALTER TABLE tasks ADD COLUMN task_type_id UUID REFERENCES task_types(id) ON DELETE SET NULL;
