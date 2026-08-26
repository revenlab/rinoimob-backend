-- Set the production monthly prices for the global billing catalog.

UPDATE billing_plans
SET monthly_price = CASE code
    WHEN 'FREE' THEN 0.00
    WHEN 'STARTER' THEN 99.90
    WHEN 'PRIME' THEN 399.90
    WHEN 'ULTIMATE' THEN 799.90
    ELSE monthly_price
END,
updated_at = NOW()
WHERE tenant_id IS NULL
  AND code IN ('FREE', 'STARTER', 'PRIME', 'ULTIMATE');
