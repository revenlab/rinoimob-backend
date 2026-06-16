package com.rinoimob.service.crm;

import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
}
