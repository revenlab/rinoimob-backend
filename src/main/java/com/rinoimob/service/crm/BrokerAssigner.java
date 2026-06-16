package com.rinoimob.service.crm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.LeadPoolBrokerSelectionMode;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import com.rinoimob.domain.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerAssigner {

    private final UserRepository userRepository;

    // key: tenantId:poolId -> pointer
    private final Map<String, AtomicInteger> pointers = new ConcurrentHashMap<>();

    public UUID chooseBroker(UUID tenantId, UUID poolId) {
        List<User> brokers = userRepository.findByTenantIdAndActive(tenantId, Boolean.TRUE);
        if (brokers == null || brokers.isEmpty()) return null;
        String key = tenantId.toString() + ":" + (poolId != null ? poolId.toString() : "null");
        AtomicInteger ai = pointers.computeIfAbsent(key, k -> new AtomicInteger(0));
        int idx = Math.abs(ai.getAndIncrement()) % brokers.size();
        User chosen = brokers.get(idx);
        if (chosen == null) return null;
        return chosen.getId();
    }

    public UUID chooseBroker(UUID tenantId, LeadPool pool) {
        if (pool == null) {
            return chooseBroker(tenantId, (UUID) null);
        }
        if (pool.getRoutingStrategy() == LeadPoolRoutingStrategy.OPEN_TO_ALL) {
            return null;
        }

        List<User> brokers;
        if (pool.getBrokerSelectionMode() == LeadPoolBrokerSelectionMode.SPECIFIC_BROKERS) {
            brokers = (pool.getBrokers() == null ? List.<User>of() : pool.getBrokers()).stream()
                    .filter(user -> Boolean.TRUE.equals(user.getActive()))
                    .filter(user -> tenantId.equals(user.getTenantId()))
                    .collect(Collectors.toList());
        } else {
            brokers = userRepository.findByTenantIdAndActive(tenantId, Boolean.TRUE);
        }

        if (brokers == null || brokers.isEmpty()) {
            return null;
        }

        String key = tenantId.toString() + ":" + pool.getId();
        AtomicInteger ai = pointers.computeIfAbsent(key, k -> new AtomicInteger(0));
        int idx = Math.abs(ai.getAndIncrement()) % brokers.size();
        User chosen = brokers.get(idx);
        return chosen == null ? null : chosen.getId();
    }
}
