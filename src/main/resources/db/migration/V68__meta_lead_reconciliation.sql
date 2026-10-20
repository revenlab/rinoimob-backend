ALTER TABLE integration_routes
    ADD COLUMN meta_sync_from TIMESTAMPTZ,
    ADD COLUMN meta_sync_until TIMESTAMPTZ,
    ADD COLUMN meta_sync_cursor TEXT,
    ADD COLUMN meta_synced_until TIMESTAMPTZ,
    ADD COLUMN meta_last_sync_at TIMESTAMPTZ,
    ADD COLUMN meta_next_sync_at TIMESTAMPTZ NOT NULL DEFAULT 'epoch',
    ADD COLUMN meta_sync_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN meta_sync_error VARCHAR(500),
    ADD COLUMN meta_recovered_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_integration_routes_meta_sync_due
    ON integration_routes(meta_next_sync_at, id) WHERE active = TRUE;

CREATE UNIQUE INDEX uq_lead_acquisitions_connection_external_lead
    ON lead_acquisitions(connection_id, external_lead_id);
