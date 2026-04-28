package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PropertySpecification {

    public static Specification<Property> withFilters(
            UUID tenantId,
            PropertyStatus status,
            PropertyOperation operation,
            PropertyType propertyType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            String city) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (operation != null) {
                predicates.add(cb.equal(root.get("operation"), operation));
            }
            if (propertyType != null) {
                predicates.add(cb.equal(root.get("propertyType"), propertyType));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (bedrooms != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bedrooms"), bedrooms));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("addressCity")),
                        "%" + city.toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
