CREATE TABLE property_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL,
    seaweed_fid VARCHAR(100),
    url TEXT NOT NULL,
    youtube_video_id VARCHAR(32),
    title VARCHAR(120),
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_property_videos_tenant_id ON property_videos(tenant_id);
CREATE INDEX idx_property_videos_property_id ON property_videos(property_id);
