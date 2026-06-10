package com.rinoimob.service.crm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
}
