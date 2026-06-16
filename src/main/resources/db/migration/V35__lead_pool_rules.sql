-- Add criteria, priority and routing strategy to lead_pools
ALTER TABLE lead_pools
    ADD COLUMN criteria jsonb,
    ADD COLUMN priority integer DEFAULT 100,
    ADD COLUMN routing_strategy varchar(32) DEFAULT 'ROUND_ROBIN';

-- Create index for tenant and priority to speed up rule evaluation
CREATE INDEX lead_pools_tenant_priority_idx ON lead_pools (tenant_id, priority);
