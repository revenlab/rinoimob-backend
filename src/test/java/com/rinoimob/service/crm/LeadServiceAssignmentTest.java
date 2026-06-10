package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateLeadRequest;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceAssignmentTest {

    @Mock private LeadRepository leadRepository;
    @Mock private com.rinoimob.domain.repository.LeadNoteRepository leadNoteRepository;
    @Mock private com.rinoimob.domain.repository.LeadEventRepository leadEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.rinoimob.domain.repository.LeadPropertyRepository leadPropertyRepository;
    @Mock private com.rinoimob.domain.repository.PropertyRepository propertyRepository;
    @Mock private com.rinoimob.service.automation.workflow.AutomationEventDispatcher automationEventDispatcher;
    @Mock private LeadRealtimeService leadRealtimeService;
    @Mock private com.rinoimob.service.billing.TenantQuotaEnforcementService tenantQuotaEnforcementService;
    @Mock private LeadPoolRepository leadPoolRepository;

    @Test
    void createLeadAutoAssignsPoolAndBroker() {
        UUID tenant = UUID.randomUUID();
        UUID poolId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();

        LeadPool pool = new LeadPool(poolId, tenant, "P", null, LocalDateTime.now(), "{\"source\":\"WHATSAPP\"}", 100, "ROUND_ROBIN");
        when(leadPoolRepository.findByTenantIdOrderByPriorityAsc(tenant)).thenReturn(List.of(pool));

        User broker = new User(); broker.setId(brokerId); broker.setActive(true);
        when(userRepository.findByTenantIdAndActive(tenant, Boolean.TRUE)).thenReturn(List.of(broker));

        org.mockito.Mockito.doNothing().when(tenantQuotaEnforcementService).assertCanCreateLead(tenant);

        Lead saved = new Lead(); saved.setId(UUID.randomUUID());
        when(leadRepository.save(any())).thenReturn(saved);

        LeadPoolRuleEvaluator evaluator = new LeadPoolRuleEvaluator(leadPoolRepository, propertyRepository);
        BrokerAssigner assigner = new BrokerAssigner(userRepository);

        LeadService svc = new LeadService(leadRepository, leadNoteRepository, leadEventRepository, userRepository,
                leadPropertyRepository, propertyRepository, automationEventDispatcher, leadRealtimeService, tenantQuotaEnforcementService,
                evaluator, assigner);

        CreateLeadRequest req = new CreateLeadRequest("Name", "a@b.com", "123", "hello", null, "WHATSAPP");
        svc.create(tenant, req);

        ArgumentCaptor<Lead> cap = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(cap.capture());
        Lead persisted = cap.getValue();
        assertThat(persisted.getPoolId()).isEqualTo(poolId);
        assertThat(persisted.getAssignedTo()).isEqualTo(brokerId);
    }

}

