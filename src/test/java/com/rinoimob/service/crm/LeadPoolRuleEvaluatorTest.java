package com.rinoimob.service.crm;

import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadPoolRuleEvaluatorTest {

    @Mock private LeadPoolRepository poolRepo;
    @Mock private PropertyRepository propertyRepository;

    @Test
    void shouldMatchBySourceAndKeywordAndPriority() throws Exception {
        UUID tenant = UUID.randomUUID();
        LeadPool p1 = new LeadPool(UUID.randomUUID(), tenant, "P1", null, LocalDateTime.now(), "{\"source\":\"WEB\"}", 200, "ROUND_ROBIN");
        LeadPool p2 = new LeadPool(UUID.randomUUID(), tenant, "P2", null, LocalDateTime.now(), "{\"source\":\"API\"}", 100, "ROUND_ROBIN");
        when(poolRepo.findByTenantIdOrderByPriorityAsc(tenant)).thenReturn(List.of(p2, p1));

        LeadPoolRuleEvaluator eval = new LeadPoolRuleEvaluator(poolRepo, propertyRepository);

        Lead lead = new Lead();
        lead.setSource("API");
        UUID matched = eval.evaluate(tenant, lead);
        assertThat(matched).isEqualTo(p2.getId());
    }

    @Test
    void shouldMatchPropertyCriteriaAndKeyword() throws Exception {
        UUID tenant = UUID.randomUUID();
        LeadPool p = new LeadPool(UUID.randomUUID(), tenant, "P", null, LocalDateTime.now(), "{\"city\":\"Rio\", \"minPrice\": 100000}", 100, "ROUND_ROBIN");
        when(poolRepo.findByTenantIdOrderByPriorityAsc(tenant)).thenReturn(List.of(p));

        Lead lead = new Lead();
        lead.setPropertyId(UUID.randomUUID());

        Property prop = new Property();
        prop.setId(lead.getPropertyId());
        prop.setAddressCity("Rio");
        prop.setPrice(new BigDecimal("150000"));
        when(propertyRepository.findById(lead.getPropertyId())).thenReturn(Optional.of(prop));

        LeadPoolRuleEvaluator eval = new LeadPoolRuleEvaluator(poolRepo, propertyRepository);
        UUID matched = eval.evaluate(tenant, lead);
        assertThat(matched).isEqualTo(p.getId());
    }
}
