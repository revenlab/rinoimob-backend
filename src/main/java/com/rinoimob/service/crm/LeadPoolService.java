package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.LeadPoolBrokerSelectionMode;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class LeadPoolService {

    private final LeadPoolRepository leadPoolRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LeadPoolResponse> list(UUID tenantId) {
        return leadPoolRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LeadPoolResponse create(UUID tenantId, CreateLeadPoolRequest req) {
        LeadPool p = new LeadPool();
        p.setTenantId(tenantId);
        applyRequest(tenantId, p, req.name(), req.description(), req.criteria(), req.priority(),
                req.routingStrategy(), req.brokerSelectionMode(), req.brokerIds(), req.triggerAfterInactiveDays());
        p = leadPoolRepository.save(p);
        return toResponse(p);
    }

    @Transactional
    public LeadPoolResponse update(UUID tenantId, UUID id, UpdateLeadPoolRequest req) {
        LeadPool p = leadPoolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead pool not found"));
        applyRequest(tenantId, p, req.name(), req.description(), req.criteria(), req.priority(),
                req.routingStrategy(), req.brokerSelectionMode(), req.brokerIds(), req.triggerAfterInactiveDays());
        p = leadPoolRepository.save(p);
        return toResponse(p);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        LeadPool p = leadPoolRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead pool not found"));
        leadPoolRepository.deleteByIdAndTenantId(id, tenantId);
    }

    private void applyRequest(UUID tenantId, LeadPool pool, String name, String description, String criteria, Integer priority,
                              String routingStrategy, String brokerSelectionMode, List<UUID> brokerIds, Integer triggerAfterInactiveDays) {
        if (name != null) {
            pool.setName(name);
        }
        if (description != null) {
            pool.setDescription(description);
        }
        if (criteria != null) {
            pool.setCriteria(criteria);
        }
        if (priority != null) {
            pool.setPriority(priority);
        }
        if (routingStrategy != null) {
            pool.setRoutingStrategy(parseRoutingStrategy(routingStrategy));
        }
        if (brokerSelectionMode != null) {
            pool.setBrokerSelectionMode(parseBrokerSelectionMode(brokerSelectionMode));
        }
        if (triggerAfterInactiveDays != null) {
            pool.setTriggerAfterInactiveDays(triggerAfterInactiveDays);
        }
        if (pool.getBrokerSelectionMode() == LeadPoolBrokerSelectionMode.SPECIFIC_BROKERS) {
            if (brokerIds == null) {
                if (pool.getBrokers() == null || pool.getBrokers().isEmpty()) {
                    throw new ResponseStatusException(BAD_REQUEST, "At least one broker must be selected for SPECIFIC_BROKERS pools");
                }
            } else if (brokerIds.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "At least one broker must be selected for SPECIFIC_BROKERS pools");
            } else {
                pool.setBrokers(resolveBrokers(tenantId, brokerIds));
            }
        } else {
            pool.setBrokers(new HashSet<>());
        }
    }

    private LeadPoolRoutingStrategy parseRoutingStrategy(String routingStrategy) {
        try {
            return LeadPoolRoutingStrategy.valueOf(routingStrategy);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid routingStrategy: " + routingStrategy, e);
        }
    }

    private LeadPoolBrokerSelectionMode parseBrokerSelectionMode(String brokerSelectionMode) {
        try {
            return LeadPoolBrokerSelectionMode.valueOf(brokerSelectionMode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid brokerSelectionMode: " + brokerSelectionMode, e);
        }
    }

    private Set<User> resolveBrokers(UUID tenantId, List<UUID> brokerIds) {
        Set<User> brokers = new HashSet<>();
        for (UUID brokerId : brokerIds) {
            User user = userRepository.findByIdAndTenantId(brokerId, tenantId)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Broker not found for tenant: " + brokerId));
            brokers.add(user);
        }
        return brokers;
    }

    private LeadPoolResponse toResponse(LeadPool pool) {
        return new LeadPoolResponse(
                pool.getId(),
                pool.getTenantId(),
                pool.getName(),
                pool.getDescription(),
                pool.getCriteria(),
                pool.getPriority(),
                pool.getRoutingStrategy() != null ? pool.getRoutingStrategy().name() : null,
                pool.getBrokerSelectionMode() != null ? pool.getBrokerSelectionMode().name() : null,
                pool.getTriggerAfterInactiveDays(),
                pool.getBrokers().stream().map(User::getId).collect(Collectors.toList()),
                pool.getCreatedAt()
        );
    }
}
