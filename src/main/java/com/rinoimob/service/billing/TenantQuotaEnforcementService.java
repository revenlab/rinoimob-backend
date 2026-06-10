package com.rinoimob.service.billing;

import com.rinoimob.domain.dto.billing.TenantBillingLimitsSnapshot;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.domain.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TenantQuotaEnforcementService {

    private final TenantBillingProfileService tenantBillingProfileService;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final LeadRepository leadRepository;

    public TenantQuotaEnforcementService(TenantBillingProfileService tenantBillingProfileService,
                                         UserRepository userRepository,
                                         PropertyRepository propertyRepository,
                                         LeadRepository leadRepository) {
        this.tenantBillingProfileService = tenantBillingProfileService;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.leadRepository = leadRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanCreateUser(UUID tenantId) {
        TenantBillingLimitsSnapshot limits = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        long currentActiveUsers = userRepository.countByTenantIdAndActive(tenantId, true);
        assertUnderLimit(currentActiveUsers, limits.maxUsers(), "limite de usuários atingido");
    }

    @Transactional(readOnly = true)
    public void assertCanCreateProperty(UUID tenantId) {
        TenantBillingLimitsSnapshot limits = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        long currentProperties = propertyRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
        assertUnderLimit(currentProperties, limits.maxProperties(), "limite de imóveis atingido");
    }

    @Transactional(readOnly = true)
    public void assertCanCreateLead(UUID tenantId) {
        TenantBillingLimitsSnapshot limits = tenantBillingProfileService.resolveEffectiveLimits(tenantId);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long currentLeadsInMonth = leadRepository.countByTenantIdAndCreatedAtAfterAndDeletedAtIsNull(tenantId, monthStart);
        assertUnderLimit(currentLeadsInMonth, limits.maxLeadsPerMonth(), "limite de leads do mês atingido");
    }

    private void assertUnderLimit(long currentCount, int maxLimit, String message) {
        if (maxLimit == TenantBillingLimitsSnapshot.UNLIMITED) {
            return;
        }
        if (currentCount >= maxLimit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }
}
