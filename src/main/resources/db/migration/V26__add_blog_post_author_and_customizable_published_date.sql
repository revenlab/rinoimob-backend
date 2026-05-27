-- Add author tracking and customizable published date to blog posts
ALTER TABLE tenant_blog_posts ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE tenant_blog_posts ADD COLUMN updated_by UUID REFERENCES users(id);

-- Create index for efficient author queries
CREATE INDEX idx_tenant_blog_posts_created_by ON tenant_blog_posts(tenant_id, created_by);
CREATE INDEX idx_tenant_blog_posts_updated_by ON tenant_blog_posts(tenant_id, updated_by);
