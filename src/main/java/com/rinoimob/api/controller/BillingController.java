package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.dto.BillingCustomerDetailsResponse;
import com.rinoimob.domain.dto.BillingInvoiceResponse;
import com.rinoimob.domain.dto.BillingStatusResponse;
import com.rinoimob.domain.dto.TenantBillingPortalResponse;
import com.rinoimob.domain.dto.UpdateBillingCardTokenRequest;
import com.rinoimob.domain.dto.UpdateBillingCustomerDetailsRequest;
import com.rinoimob.domain.enums.BillingPaymentStatus;
import com.rinoimob.service.billing.TenantBillingPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Tenant billing portal endpoints")
public class BillingController {

    private final TenantBillingPortalService tenantBillingPortalService;

    public BillingController(TenantBillingPortalService tenantBillingPortalService) {
        this.tenantBillingPortalService = tenantBillingPortalService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current tenant billing data for portal")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Tenant billing management view"))
    public TenantBillingPortalResponse getMyBilling() {
        UUID tenantId = requireTenantId();
        return tenantBillingPortalService.getCurrentBilling(tenantId);
    }

    @GetMapping("/status")
    @Operation(summary = "Get tenant billing status without financial personal data")
    @PreAuthorize("isAuthenticated()")
    public BillingStatusResponse getBillingStatus() {
        return tenantBillingPortalService.getBillingStatus(requireTenantId());
    }

    @GetMapping("/invoices")
    @Operation(summary = "List tenant invoices and payments")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public Page<BillingInvoiceResponse> listInvoices(
            @RequestParam(required = false) List<BillingPaymentStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tenantBillingPortalService.listInvoices(requireTenantId(), status, page, size);
    }

    @PostMapping("/checkout")
    @Operation(summary = "Start checkout for a target plan")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkout created or downgrade scheduled"),
            @ApiResponse(responseCode = "409", description = "Plan change conflicts with current billing state")
    })
    public StartBillingCheckoutResponse startCheckout(@Valid @RequestBody StartBillingCheckoutRequest request,
                                                      HttpServletRequest httpRequest) {
        UUID tenantId = requireTenantId();
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return tenantBillingPortalService.startCheckout(tenantId, userId, request);
    }

    @PutMapping("/customer")
    @Operation(summary = "Update tenant billing customer details for Asaas checkout")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public BillingCustomerDetailsResponse updateBillingCustomer(
            @Valid @RequestBody UpdateBillingCustomerDetailsRequest request,
            HttpServletRequest httpRequest) {
        UUID tenantId = requireTenantId();
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return tenantBillingPortalService.updateBillingCustomer(tenantId, userId, request);
    }

    @PostMapping("/cancel")
    @Operation(summary = "Schedule subscription cancellation for the end of the paid period")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public TenantBillingPortalResponse cancelSubscription() {
        return tenantBillingPortalService.scheduleCancellation(requireTenantId());
    }

    @PostMapping("/reactivate")
    @Operation(summary = "Undo a subscription cancellation before the paid period ends")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    public TenantBillingPortalResponse reactivateSubscription() {
        return tenantBillingPortalService.reactivateCancellation(requireTenantId());
    }

    @PutMapping("/payment-method/card-token")
    @Operation(summary = "Update the recurring card using an Asaas token")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_OWNER')")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Card token accepted by Asaas"),
            @ApiResponse(responseCode = "501", description = "Tokenized card update is disabled")
    })
    public ResponseEntity<Void> updateCardToken(@Valid @RequestBody UpdateBillingCardTokenRequest request,
                                                 HttpServletRequest httpRequest) {
        tenantBillingPortalService.updateCardToken(requireTenantId(), request.creditCardToken(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private UUID requireTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant context not found");
        }
        return UUID.fromString(tenantId);
    }
}
