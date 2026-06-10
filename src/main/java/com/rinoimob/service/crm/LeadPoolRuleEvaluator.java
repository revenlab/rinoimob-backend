package com.rinoimob.service.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.entity.LeadPool;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.repository.LeadPoolRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadPoolRuleEvaluator {

    private final LeadPoolRepository leadPoolRepository;
    private final PropertyRepository propertyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UUID evaluate(UUID tenantId, Lead lead) {
        List<LeadPool> pools = leadPoolRepository.findByTenantIdOrderByPriorityAsc(tenantId);
        for (LeadPool p : pools) {
            if (p.getCriteria() == null || p.getCriteria().isBlank()) {
                // empty criteria matches everything
                return p.getId();
            }
            try {
                JsonNode node = objectMapper.readTree(p.getCriteria());
                if (matches(node, lead)) {
                    return p.getId();
                }
            } catch (Exception e) {
                // malformed criteria - skip
            }
        }
        return null;
    }

    private boolean matches(JsonNode node, Lead lead) {
        // source
        if (node.has("source")) {
            String src = node.get("source").asText(null);
            if (src != null && (lead.getSource() == null || !lead.getSource().equalsIgnoreCase(src))) return false;
        }
        // city (match property.addressCity if property present)
        if (node.has("city")) {
            String city = node.get("city").asText(null);
            String leadCity = null;
            if (lead.getPropertyId() != null) {
                Property prop = propertyRepository.findById(lead.getPropertyId()).orElse(null);
                if (prop != null) leadCity = prop.getAddressCity();
            }
            if (city != null) {
                if (leadCity == null || !leadCity.equalsIgnoreCase(city)) return false;
            }
        }
        // propertyType
        if (node.has("propertyType")) {
            String pt = node.get("propertyType").asText(null);
            if (pt != null) {
                if (lead.getPropertyId() == null) return false;
                Property prop = propertyRepository.findById(lead.getPropertyId()).orElse(null);
                if (prop == null || prop.getPropertyType() == null || !prop.getPropertyType().name().equalsIgnoreCase(pt)) return false;
            }
        }
        // minPrice
        if (node.has("minPrice")) {
            BigDecimal min = node.get("minPrice").decimalValue();
            if (lead.getPropertyId() == null) return false;
            Property prop = propertyRepository.findById(lead.getPropertyId()).orElse(null);
            if (prop == null || prop.getPrice() == null || prop.getPrice().compareTo(min) < 0) return false;
        }
        // maxPrice
        if (node.has("maxPrice")) {
            BigDecimal max = node.get("maxPrice").decimalValue();
            if (lead.getPropertyId() == null) return false;
            Property prop = propertyRepository.findById(lead.getPropertyId()).orElse(null);
            if (prop == null || prop.getPrice() == null || prop.getPrice().compareTo(max) > 0) return false;
        }
        // keywordContains on lead.message and lead.name
        if (node.has("keywordContains")) {
            String kw = node.get("keywordContains").asText(null);
            if (kw != null) {
                String msg = lead.getMessage() != null ? lead.getMessage() : "";
                String nm = lead.getName() != null ? lead.getName() : "";
                String lower = kw.toLowerCase();
                if (!msg.toLowerCase().contains(lower) && !nm.toLowerCase().contains(lower)) return false;
            }
        }
        return true;
    }
}
