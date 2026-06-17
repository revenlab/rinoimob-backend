package com.rinoimob.service.crm;

import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.LeadEvent;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.LeadPoolBrokerSelectionMode;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.repository.LeadEventRepository;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadPoolInactivitySchedulerTest {

    @Mock private LeadPoolRepository leadPoolRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private LeadEventRepository leadEventRepository;
    @Mock private LeadPoolRuleEvaluator leadPoolRuleEvaluator;
    @Mock private BrokerAssigner brokerAssigner;

    @Test
    void shouldMoveInactiveLeadToOpenToAllPoolAndClearAssignment() {
        UUID tenant = UUID.randomUUID();
        LeadPool pool = new LeadPool();
        pool.setId(UUID.randomUUID());
        pool.setTenantId(tenant);
        pool.setName("Inactive pool");
        pool.setPriority(10);
        pool.setTriggerAfterInactiveDays(7);
        pool.setRoutingStrategy(LeadPoolRoutingStrategy.OPEN_TO_ALL);
        pool.setBrokerSelectionMode(LeadPoolBrokerSelectionMode.ALL_BROKERS);
        pool.setCriteria("{\"source\":\"WEB\"}");

        Lead lead = new Lead();
        lead.setId(UUID.randomUUID());
        lead.setTenantId(tenant);
        lead.setSource("WEB");
        lead.setStatus(LeadStatus.NEW);
        lead.setUpdatedAt(LocalDateTime.now().minusDays(10));
        lead.setAssignedTo(UUID.randomUUID());

        when(leadPoolRepository.findInactivityPools()).thenReturn(List.of(pool));
        when(leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(any(), anyList(), any()))
                .thenReturn(List.of(lead));
        when(leadPoolRuleEvaluator.matches(pool, lead)).thenReturn(true);

        LeadPoolInactivityScheduler scheduler = new LeadPoolInactivityScheduler(
                leadPoolRepository, leadRepository, leadEventRepository, leadPoolRuleEvaluator, brokerAssigner
        );
        scheduler.scanInactiveLeadPools();

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getPoolId()).isEqualTo(pool.getId());
        assertThat(captor.getValue().getAssignedTo()).isNull();
        verify(brokerAssigner, never()).chooseBroker(any(), any(LeadPool.class));
        verify(leadEventRepository).save(any(LeadEvent.class));
    }

    @Test
    void shouldAssignSpecificBrokerForInactivePool() {
        UUID tenant = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();

        User broker = new User();
        broker.setId(brokerId);
        broker.setTenantId(tenant);
        broker.setActive(true);

        LeadPool pool = new LeadPool();
        pool.setId(UUID.randomUUID());
        pool.setTenantId(tenant);
        pool.setName("Specific pool");
        pool.setPriority(10);
        pool.setTriggerAfterInactiveDays(7);
        pool.setRoutingStrategy(LeadPoolRoutingStrategy.ROUND_ROBIN);
        pool.setBrokerSelectionMode(LeadPoolBrokerSelectionMode.SPECIFIC_BROKERS);
        pool.setBrokers(Set.of(broker));
        pool.setCriteria("{}");

        Lead lead = new Lead();
        lead.setId(UUID.randomUUID());
        lead.setTenantId(tenant);
        lead.setStatus(LeadStatus.NEW);
        lead.setUpdatedAt(LocalDateTime.now().minusDays(10));

        when(leadPoolRepository.findInactivityPools()).thenReturn(List.of(pool));
        when(leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(any(), anyList(), any()))
                .thenReturn(List.of(lead));
        when(leadPoolRuleEvaluator.matches(pool, lead)).thenReturn(true);
        when(brokerAssigner.chooseBroker(tenant, pool)).thenReturn(brokerId);

        LeadPoolInactivityScheduler scheduler = new LeadPoolInactivityScheduler(
                leadPoolRepository, leadRepository, leadEventRepository, leadPoolRuleEvaluator, brokerAssigner
        );
        scheduler.scanInactiveLeadPools();

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getPoolId()).isEqualTo(pool.getId());
        assertThat(captor.getValue().getAssignedTo()).isEqualTo(brokerId);
        verify(leadEventRepository).save(any(LeadEvent.class));
    }
}
