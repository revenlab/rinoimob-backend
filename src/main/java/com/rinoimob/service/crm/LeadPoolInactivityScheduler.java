package com.rinoimob.service.crm;

import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadEvent;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.enums.LeadEventType;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.repository.LeadEventRepository;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadPoolInactivityScheduler {

    private static final List<LeadStatus> CLOSED_STATUSES = List.of(LeadStatus.LOST, LeadStatus.WON);

    private final LeadPoolRepository leadPoolRepository;
    private final LeadRepository leadRepository;
    private final LeadEventRepository leadEventRepository;
    private final LeadPoolRuleEvaluator leadPoolRuleEvaluator;
    private final BrokerAssigner brokerAssigner;

    @Scheduled(fixedDelayString = "${lead-pool.inactivity-scan-ms:600000}")
    @Transactional
    public void scanInactiveLeadPools() {
        Set<UUID> processedLeads = new HashSet<>();

        List<LeadPool> allPools = leadPoolRepository.findInactivityPools();

        for (LeadPool pool : allPools) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(pool.getTriggerAfterInactiveDays());
            List<Lead> leads = leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(
                    pool.getTenantId(), CLOSED_STATUSES, cutoff);

            for (Lead lead : leads) {
                if (lead.getPoolId() != null && lead.getPoolId().equals(pool.getId())) {
                    processedLeads.add(lead.getId());
                    continue;
                }
                if (processedLeads.contains(lead.getId())) {
                    continue;
                }
                if (!leadPoolRuleEvaluator.matches(pool, lead)) {
                    continue;
                }

                lead.setPoolId(pool.getId());
                if (pool.getRoutingStrategy() == LeadPoolRoutingStrategy.OPEN_TO_ALL) {
                    lead.setAssignedTo(null);
                } else {
                    lead.setAssignedTo(brokerAssigner.chooseBroker(pool.getTenantId(), pool));
                }
                leadRepository.save(lead);
                logActivity(lead, pool, cutoff);
                processedLeads.add(lead.getId());
            }
        }
    }

    private void logActivity(Lead lead, LeadPool pool, LocalDateTime cutoff) {
        LeadEvent event = new LeadEvent();
        event.setLeadId(lead.getId());
        event.setEventType(LeadEventType.ASSIGNED);
        event.setDescription("Lead movido para bolsão " + pool.getName() + " após inatividade desde " + cutoff);
        leadEventRepository.save(event);
        log.info("Lead {} moved to inactivity pool {} tenant={}", lead.getId(), pool.getId(), pool.getTenantId());
    }
}
