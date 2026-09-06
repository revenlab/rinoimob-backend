ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS available_to_all_brokers BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS property_broker_map (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (property_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_property_broker_map_user ON property_broker_map(user_id);
