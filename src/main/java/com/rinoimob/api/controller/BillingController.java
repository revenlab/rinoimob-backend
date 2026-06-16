package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.StartBillingCheckoutRequest;
import com.rinoimob.domain.dto.StartBillingCheckoutResponse;
import com.rinoimob.domain.dto.TenantBillingPortalResponse;
import com.rinoimob.service.billing.TenantBillingPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
    public TenantBillingPortalResponse getMyBilling() {
        UUID tenantId = requireTenantId();
        return tenantBillingPortalService.getCurrentBilling(tenantId);
    }

    @PostMapping("/checkout")
    @Operation(summary = "Start checkout for a target plan")
    public StartBillingCheckoutResponse startCheckout(@Valid @RequestBody StartBillingCheckoutRequest request,
                                                      HttpServletRequest httpRequest) {
        UUID tenantId = requireTenantId();
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return tenantBillingPortalService.startCheckout(tenantId, userId, request);
    }

    private UUID requireTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant context not found");
        }
        return UUID.fromString(tenantId);
    }
}
