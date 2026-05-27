ALTER TABLE tenant_website_config
    ADD COLUMN IF NOT EXISTS about_page_title        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS about_page_subtitle     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS about_page_description  TEXT,
    ADD COLUMN IF NOT EXISTS about_image_fid         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS about_image_url         VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS about_mission           TEXT,
    ADD COLUMN IF NOT EXISTS about_vision            TEXT,
    ADD COLUMN IF NOT EXISTS about_values            TEXT,
    ADD COLUMN IF NOT EXISTS about_founded_year      VARCHAR(10),
    ADD COLUMN IF NOT EXISTS about_team_count        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS about_properties_count  VARCHAR(50);
