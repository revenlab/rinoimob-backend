package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateLeadPoolRequest;
import com.rinoimob.domain.dto.LeadPoolResponse;
import com.rinoimob.domain.dto.UpdateLeadPoolRequest;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.repository.LeadPoolRepository;
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
class LeadPoolServiceTest {

    @Mock private LeadPoolRepository leadPoolRepository;

    @Test
    void shouldCreateAndListAndDeletePools() {
        UUID tenantId = UUID.randomUUID();

        LeadPoolService service = new LeadPoolService(leadPoolRepository);

        LeadPool saved = new LeadPool(UUID.randomUUID(), tenantId, "Test", "desc", LocalDateTime.now());
        when(leadPoolRepository.save(any())).thenReturn(saved);
        when(leadPoolRepository.findByTenantId(tenantId)).thenReturn(List.of(saved));
        when(leadPoolRepository.findByIdAndTenantId(saved.getId(), tenantId)).thenReturn(Optional.of(saved));

        CreateLeadPoolRequest req = new CreateLeadPoolRequest("Test", "desc");
        LeadPoolResponse resp = service.create(tenantId, req);
        assertThat(resp.id()).isEqualTo(saved.getId());
        assertThat(resp.name()).isEqualTo("Test");

        List<LeadPoolResponse> list = service.list(tenantId);
        assertThat(list).hasSize(1);

        UpdateLeadPoolRequest upd = new UpdateLeadPoolRequest("NewName", null);
        LeadPool updatedEntity = new LeadPool(saved.getId(), tenantId, "NewName", "desc", saved.getCreatedAt());
        when(leadPoolRepository.save(any())).thenReturn(updatedEntity);
        LeadPoolResponse updated = service.update(tenantId, saved.getId(), upd);
        assertThat(updated.name()).isEqualTo("NewName");

        service.delete(tenantId, saved.getId());
        verify(leadPoolRepository).deleteByIdAndTenantId(saved.getId(), tenantId);
    }
}
