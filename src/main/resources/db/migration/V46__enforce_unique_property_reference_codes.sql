WITH normalized_references AS (
    SELECT
        id,
        NULLIF(UPPER(BTRIM(reference_code)), '') AS normalized_code,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, NULLIF(UPPER(BTRIM(reference_code)), '')
            ORDER BY created_at, id
        ) AS duplicate_position
    FROM properties
)
UPDATE properties property
SET reference_code = CASE
    WHEN normalized.normalized_code IS NULL THEN
        'IMV-' || UPPER(SUBSTRING(REPLACE(property.id::TEXT, '-', '') FROM 1 FOR 8))
    WHEN normalized.duplicate_position > 1 THEN
        LEFT(normalized.normalized_code, 37) || '-' ||
            UPPER(SUBSTRING(REPLACE(property.id::TEXT, '-', '') FROM 1 FOR 12))
    ELSE normalized.normalized_code
END
FROM normalized_references normalized
WHERE property.id = normalized.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_properties_tenant_reference_code
    ON properties (tenant_id, reference_code)
    WHERE reference_code IS NOT NULL;
