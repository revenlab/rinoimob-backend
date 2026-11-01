package com.rinoimob.domain.enums;

public final class RentalEnums {
    private RentalEnums() {}

    public enum PartyType { INDIVIDUAL, LEGAL_ENTITY }
    public enum PartyRole { OWNER, TENANT, GUARANTOR }
    public enum ContractStatus { DRAFT, ACTIVE, EXPIRED, TERMINATED, CANCELLED }
    public enum ChargeStatus { DRAFT, ISSUING, PENDING, RECEIVED, OVERDUE, CANCELLED, REFUNDED, ERROR }
    public enum PayoutStatus { DRAFT, READY, PROCESSING, PAID, FAILED, CANCELLED }
    public enum ManagementFeeType { PERCENTAGE, FIXED }
    public enum GuaranteeType { NONE, DEPOSIT, GUARANTOR, INSURANCE, CAPITALIZATION_BOND }
    public enum AdjustmentIndex { MANUAL, IPCA, IGP_M }
    public enum PayoutTreatment { OWNER, AGENCY, EXCLUDED }
    public enum AccountStatus { NOT_CONFIGURED, PENDING, ACTIVE, REJECTED, SUSPENDED }
}
