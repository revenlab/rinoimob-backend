CREATE TABLE tenant_blog_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    excerpt VARCHAR(400),
    content_html TEXT NOT NULL,
    cover_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_tenant_blog_posts_tenant_slug_active
    ON tenant_blog_posts (tenant_id, slug)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tenant_blog_posts_tenant_updated
    ON tenant_blog_posts (tenant_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tenant_blog_posts_tenant_status_published
    ON tenant_blog_posts (tenant_id, status, published_at DESC)
    WHERE deleted_at IS NULL;
