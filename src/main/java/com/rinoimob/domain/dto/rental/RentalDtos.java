package com.rinoimob.domain.dto.rental;

import com.rinoimob.domain.enums.RentalEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class RentalDtos {
    private RentalDtos() {}

    public record PartyRequest(@NotNull PartyType type, @NotBlank String name, String cpfCnpj,
                               String email, String phone, String address, String pixKey, String pixKeyType) {}
    public record PartyResponse(UUID id, PartyType type, String name, String cpfCnpj, String email,
                                String phone, String address, String pixKeyMasked, String pixKeyType) {}
    public record ContractPartyRequest(@NotNull UUID partyId, @NotNull PartyRole role,
                                       BigDecimal ownershipPercent, boolean billingResponsible) {}
    public record ContractRequest(@NotNull UUID propertyId, UUID sourceLeadId,
                                  @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                  @Min(1) @Max(28) int dueDay, @Min(0) @Max(30) int chargeLeadDays,
                                  @Min(1) @Max(28) int payoutDay, @Pattern(regexp = "BOLETO|PIX") String billingType,
                                  @NotNull @DecimalMin("0.01") BigDecimal baseRent,
                                  BigDecimal condoAmount, BigDecimal iptuAmount, BigDecimal insuranceAmount,
                                  BigDecimal discountAmount, @NotNull ManagementFeeType managementFeeType,
                                  @NotNull @DecimalMin("0") BigDecimal managementFeeValue,
                                  BigDecimal lateFeePercent, BigDecimal monthlyInterestPercent,
                                  @NotNull GuaranteeType guaranteeType, String guaranteeDetails,
                                  @NotNull AdjustmentIndex adjustmentIndex, LocalDate nextAdjustmentDate,
                                  @NotEmpty List<@Valid ContractPartyRequest> parties) {}
    public record ContractResponse(UUID id, UUID propertyId, UUID sourceLeadId, ContractStatus status,
                                   LocalDate startDate, LocalDate endDate, int dueDay, int chargeLeadDays,
                                   int payoutDay, String billingType, BigDecimal baseRent, BigDecimal condoAmount,
                                   BigDecimal iptuAmount, BigDecimal insuranceAmount, BigDecimal discountAmount,
                                   ManagementFeeType managementFeeType, BigDecimal managementFeeValue,
                                   BigDecimal lateFeePercent, BigDecimal monthlyInterestPercent,
                                   GuaranteeType guaranteeType, String guaranteeDetails,
                                   AdjustmentIndex adjustmentIndex, LocalDate nextAdjustmentDate,
                                   List<ContractPartyResponse> parties, List<DocumentResponse> documents,
                                   LocalDateTime activatedAt, LocalDateTime endedAt) {}
    public record ContractPartyResponse(UUID id, UUID partyId, String partyName, PartyRole role,
                                        BigDecimal ownershipPercent, boolean billingResponsible) {}
    public record DocumentResponse(UUID id, String name, String url, LocalDateTime createdAt) {}
    public record ChargeItemRequest(@NotBlank String type, @NotBlank String description,
                                    @NotNull BigDecimal amount, @NotNull PayoutTreatment payoutTreatment) {}
    public record GenerateChargeRequest(@NotNull LocalDate competence, @NotNull LocalDate dueDate,
                                        List<@Valid ChargeItemRequest> additionalItems) {}
    public record AdjustmentRequest(@NotNull @DecimalMin("0.01") BigDecimal newBaseRent,
                                    @NotNull LocalDate nextAdjustmentDate) {}
    public record ChargeItemResponse(UUID id, String type, String description, BigDecimal amount,
                                     PayoutTreatment payoutTreatment) {}
    public record ChargeResponse(UUID id, UUID contractId, LocalDate competence, LocalDate dueDate,
                                 ChargeStatus status, BigDecimal totalAmount, BigDecimal baseRentAmount,
                                 BigDecimal rinoFeePercent, BigDecimal rinoFeeAmount, String invoiceUrl,
                                 String receiptUrl, String billingType, List<ChargeItemResponse> items,
                                 LocalDateTime receivedAt, String errorMessage) {}
    public record PayoutResponse(UUID id, UUID contractId, UUID chargeId, UUID ownerPartyId, String ownerName,
                                 PayoutStatus status, BigDecimal grossOwnerAmount, BigDecimal managementFeeAmount,
                                 BigDecimal netAmount, LocalDate scheduledDate, LocalDateTime approvedAt,
                                 LocalDateTime completedAt, String errorMessage) {}
    public record DashboardResponse(long activeContracts, long overdueCharges, long pendingPayouts,
                                    BigDecimal receivedThisMonth) {}
    public record ProvisionAccountRequest(@NotBlank String name, @Email String email, @NotBlank String cpfCnpj,
                                          @NotBlank String mobilePhone, @NotBlank String address,
                                          @NotBlank String addressNumber, @NotBlank String province,
                                          @NotBlank String postalCode, @DecimalMin("0") BigDecimal rinoFeePercent) {}
    public record AccountResponse(AccountStatus status, String providerAccountId, String walletId,
                                  BigDecimal rinoFeePercent, boolean configured) {}
}
