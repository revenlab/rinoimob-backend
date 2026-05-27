-- Set NOT NULL constraint on slug column
-- First, fill any NULL slugs with auto-generated ones based on property type and ID
UPDATE properties 
SET slug = LOWER(COALESCE(property_type, 'property')::text) || '-' || SUBSTRING(id::text, 1, 8)
WHERE slug IS NULL OR slug = '';

-- Now add the NOT NULL constraint
ALTER TABLE properties ALTER COLUMN slug SET NOT NULL;
