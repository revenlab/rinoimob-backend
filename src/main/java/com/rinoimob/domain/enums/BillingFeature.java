package com.rinoimob.domain.enums;

public enum BillingFeature {
    BLOG("Blog", BillingPlanCode.STARTER),
    CUSTOM_DOMAIN("Domínio customizado", BillingPlanCode.STARTER),
    AUTOMATION_CRM("Automações CRM", BillingPlanCode.PRIME),
    PUBLIC_API("API pública", BillingPlanCode.PRIME);

    private final String displayName;
    private final BillingPlanCode requiredPlanCode;

    BillingFeature(String displayName, BillingPlanCode requiredPlanCode) {
        this.displayName = displayName;
        this.requiredPlanCode = requiredPlanCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BillingPlanCode getRequiredPlanCode() {
        return requiredPlanCode;
    }
}
