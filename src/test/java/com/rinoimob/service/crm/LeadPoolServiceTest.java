package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.LeadPoolRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeadPoolServiceTest {

    @Mock private LeadPoolRepository leadPoolRepository;
    @Mock private UserRepository userRepository;

    @Test
    void shouldCreateAndListAndDeletePools() {
        UUID tenantId = UUID.randomUUID();

        LeadPoolService service = new LeadPoolService(leadPoolRepository, userRepository);

        LeadPool saved = new LeadPool(UUID.randomUUID(), tenantId, "Test", "desc", LocalDateTime.now(), null, 100, "ROUND_ROBIN");
        when(leadPoolRepository.save(any())).thenReturn(saved);
        when(leadPoolRepository.findByTenantIdOrderByPriorityAsc(tenantId)).thenReturn(List.of(saved));
        when(leadPoolRepository.findByIdAndTenantId(saved.getId(), tenantId)).thenReturn(Optional.of(saved));

        CreateLeadPoolRequest req = new CreateLeadPoolRequest("Test", "desc", null, 100, "ROUND_ROBIN", "ALL_BROKERS", List.of(), null);
        LeadPoolResponse resp = service.create(tenantId, req);
        assertThat(resp.id()).isEqualTo(saved.getId());
        assertThat(resp.name()).isEqualTo("Test");

        List<LeadPoolResponse> list = service.list(tenantId);
        assertThat(list).hasSize(1);

        UpdateLeadPoolRequest upd = new UpdateLeadPoolRequest("NewName", null, null, null, null, null, null, null);
        LeadPool updatedEntity = new LeadPool(saved.getId(), tenantId, "NewName", "desc", saved.getCreatedAt());
        when(leadPoolRepository.save(any())).thenReturn(updatedEntity);
        LeadPoolResponse updated = service.update(tenantId, saved.getId(), upd);
        assertThat(updated.name()).isEqualTo("NewName");

        service.delete(tenantId, saved.getId());
        verify(leadPoolRepository).deleteByIdAndTenantId(saved.getId(), tenantId);
    }

    @Test
    void shouldRejectInvalidCriteriaAndRanges() {
        UUID tenantId = UUID.randomUUID();
        LeadPoolService service = new LeadPoolService(leadPoolRepository, userRepository);

        CreateLeadPoolRequest malformedCriteria = new CreateLeadPoolRequest(
                "Invalid", null, "{bad", 100, "ROUND_ROBIN", "ALL_BROKERS", List.of(), null);
        assertThatThrownBy(() -> service.create(tenantId, malformedCriteria))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("criteria must be valid JSON");

        CreateLeadPoolRequest invalidPriceRange = new CreateLeadPoolRequest(
                "Invalid", null, "{\"minPrice\":200,\"maxPrice\":100}", 100, "ROUND_ROBIN", "ALL_BROKERS", List.of(), null);
        assertThatThrownBy(() -> service.create(tenantId, invalidPriceRange))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("minPrice cannot be greater than maxPrice");

        CreateLeadPoolRequest invalidTrigger = new CreateLeadPoolRequest(
                "Invalid", null, null, 100, "ROUND_ROBIN", "ALL_BROKERS", List.of(), -1);
        assertThatThrownBy(() -> service.create(tenantId, invalidTrigger))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("triggerAfterInactiveDays must be zero or greater");
    }

    @Test
    void shouldRequireBrokersWhenSpecificBrokerModeIsSelected() {
        UUID tenantId = UUID.randomUUID();
        LeadPoolService service = new LeadPoolService(leadPoolRepository, userRepository);

        CreateLeadPoolRequest missingBrokers = new CreateLeadPoolRequest(
                "Specific", null, null, 100, "ROUND_ROBIN", "SPECIFIC_BROKERS", List.of(), null);
        assertThatThrownBy(() -> service.create(tenantId, missingBrokers))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("At least one broker must be selected");

        UUID brokerId = UUID.randomUUID();
        User broker = new User();
        broker.setId(brokerId);
        broker.setTenantId(tenantId);
        broker.setActive(true);
        when(userRepository.findByIdAndTenantId(brokerId, tenantId)).thenReturn(Optional.of(broker));

        LeadPool saved = new LeadPool(UUID.randomUUID(), tenantId, "Specific", null, LocalDateTime.now(), null, 100, "ROUND_ROBIN");
        saved.setBrokers(java.util.Set.of(broker));
        when(leadPoolRepository.save(any())).thenReturn(saved);

        CreateLeadPoolRequest valid = new CreateLeadPoolRequest(
                "Specific", null, null, 100, "ROUND_ROBIN", "SPECIFIC_BROKERS", List.of(brokerId), null);
        LeadPoolResponse response = service.create(tenantId, valid);

        assertThat(response.brokerIds()).containsExactly(brokerId);
    }
}
