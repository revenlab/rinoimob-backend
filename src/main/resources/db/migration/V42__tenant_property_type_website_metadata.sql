ALTER TABLE tenant_property_types
    ADD COLUMN card_color VARCHAR(20),
    ADD COLUMN cover_image_fid VARCHAR(255),
    ADD COLUMN cover_image_url VARCHAR(500);
