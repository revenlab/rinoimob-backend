-- Existing properties intentionally retain an unknown creator. Do not infer
-- ownership from an offering: being able to sell a property is not authorship.
ALTER TABLE properties ADD COLUMN created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_properties_tenant_creator ON properties (tenant_id, created_by_user_id);
