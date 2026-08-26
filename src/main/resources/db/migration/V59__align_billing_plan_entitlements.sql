-- Align the catalog with the public plan matrix: VIP support starts at Ultimate.

UPDATE billing_plans
SET vip_support_enabled = FALSE,
    features = '15 usuários, 500 imóveis, 2.000 leads/mês, blog, domínio custom, automações CRM, API pública, 15 WhatsApps'
WHERE code = 'PRIME';

UPDATE tenant_billing_profiles profile
SET vip_support_enabled = FALSE,
    updated_at = NOW()
FROM billing_plans plan
WHERE profile.billing_plan_id = plan.id
  AND plan.code = 'PRIME';
