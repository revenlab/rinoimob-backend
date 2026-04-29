CREATE TABLE lead_properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    interest_level VARCHAR(20) NOT NULL DEFAULT 'UNDEFINED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (lead_id, property_id)
);
CREATE INDEX idx_lead_properties_lead_id ON lead_properties(lead_id);
CREATE INDEX idx_lead_properties_property_id ON lead_properties(property_id);

INSERT INTO lead_properties (lead_id, property_id, interest_level)
SELECT id, property_id, 'UNDEFINED'
FROM leads
WHERE property_id IS NOT NULL
ON CONFLICT (lead_id, property_id) DO NOTHING;
