ALTER TABLE integration_connections
    ADD COLUMN outbound_key VARCHAR(80);

UPDATE integration_connections
SET outbound_key = gen_random_uuid()::text
WHERE outbound_key IS NULL;

ALTER TABLE integration_connections
    ALTER COLUMN outbound_key SET NOT NULL;

CREATE UNIQUE INDEX uq_integration_connections_outbound_key
    ON integration_connections(outbound_key);
