ALTER TABLE floor_plans
    ADD COLUMN price_from NUMERIC(14, 2),
    ADD COLUMN price_to NUMERIC(14, 2),
    ADD COLUMN bedrooms INT,
    ADD COLUMN suites INT,
    ADD COLUMN bathrooms INT,
    ADD COLUMN parking INT;

ALTER TABLE floor_plans
    ADD CONSTRAINT chk_floor_plan_price_range
    CHECK (price_to IS NULL OR price_from IS NULL OR price_to >= price_from);

ALTER TABLE floor_plans
    ADD CONSTRAINT chk_floor_plan_commercial_values_non_negative
    CHECK (
        (price_from IS NULL OR price_from >= 0)
        AND (price_to IS NULL OR price_to >= 0)
        AND (bedrooms IS NULL OR bedrooms >= 0)
        AND (suites IS NULL OR suites >= 0)
        AND (bathrooms IS NULL OR bathrooms >= 0)
        AND (parking IS NULL OR parking >= 0)
    );
