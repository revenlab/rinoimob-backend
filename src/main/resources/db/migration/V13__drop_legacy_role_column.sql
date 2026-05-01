-- Drop legacy role column now that system_role + tenant_role_id handle all cases
ALTER TABLE users DROP COLUMN IF EXISTS role;
