package com.rinoimob.service.crm;

import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.domain.enums.LeadPoolBrokerSelectionMode;
import com.rinoimob.domain.enums.LeadPoolRoutingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerAssignerTest {

    @Mock private UserRepository userRepository;

    @Test
    void roundRobinCyclesThroughActiveBrokers() {
        UUID tenant = UUID.randomUUID();
        User u1 = new User(); u1.setId(UUID.randomUUID()); u1.setActive(true);
        User u2 = new User(); u2.setId(UUID.randomUUID()); u2.setActive(true);
        when(userRepository.findByTenantIdAndActive(tenant, Boolean.TRUE)).thenReturn(List.of(u1, u2));

        BrokerAssigner assigner = new BrokerAssigner(userRepository);
        UUID a1 = assigner.chooseBroker(tenant, UUID.randomUUID());
        UUID a2 = assigner.chooseBroker(tenant, UUID.randomUUID());
        UUID a3 = assigner.chooseBroker(tenant, UUID.randomUUID());

        // since different poolId keys, pointers differ; test same pool
        BrokerAssigner same = new BrokerAssigner(userRepository);
        UUID b1 = same.chooseBroker(tenant, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        UUID b2 = same.chooseBroker(tenant, UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(b1).isNotNull();
        assertThat(b2).isNotNull();
        assertThat(b1).isNotEqualTo(b2);
    }

    @Test
    void openToAllPoolsReturnNullAssignment() {
        LeadPool pool = new LeadPool();
        pool.setId(UUID.randomUUID());
        pool.setTenantId(UUID.randomUUID());
        pool.setRoutingStrategy(LeadPoolRoutingStrategy.OPEN_TO_ALL);
        pool.setBrokerSelectionMode(LeadPoolBrokerSelectionMode.ALL_BROKERS);

        BrokerAssigner assigner = new BrokerAssigner(userRepository);
        assertThat(assigner.chooseBroker(pool.getTenantId(), pool)).isNull();
    }

    @Test
    void specificBrokerPoolsUseOnlySelectedBrokers() {
        UUID tenant = UUID.randomUUID();
        User u1 = new User(); u1.setId(UUID.randomUUID()); u1.setTenantId(tenant); u1.setActive(true);
        User u2 = new User(); u2.setId(UUID.randomUUID()); u2.setTenantId(tenant); u2.setActive(true);

        LeadPool pool = new LeadPool();
        pool.setId(UUID.randomUUID());
        pool.setTenantId(tenant);
        pool.setRoutingStrategy(LeadPoolRoutingStrategy.ROUND_ROBIN);
        pool.setBrokerSelectionMode(LeadPoolBrokerSelectionMode.SPECIFIC_BROKERS);
        pool.setBrokers(Set.of(u1, u2));

        BrokerAssigner assigner = new BrokerAssigner(userRepository);
        UUID first = assigner.chooseBroker(tenant, pool);
        UUID second = assigner.chooseBroker(tenant, pool);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }
}
