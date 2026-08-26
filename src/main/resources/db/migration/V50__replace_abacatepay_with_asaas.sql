UPDATE tenant_subscriptions
SET provider = 'MANUAL',
    provider_customer_id = NULL,
    provider_subscription_id = NULL,
    provider_checkout_id = NULL
WHERE provider = 'ABACATEPAY';
