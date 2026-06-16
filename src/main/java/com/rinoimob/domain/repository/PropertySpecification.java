package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            String city,
            String queryText,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusKm) {

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
            if (categorySlug != null && !categorySlug.isBlank()) {
                query.distinct(true);
                Join<Object, Object> categoryJoin = root.join("categories", JoinType.INNER);
                predicates.add(cb.equal(categoryJoin.get("slug"), categorySlug));
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
            if (queryText != null && !queryText.isBlank()) {
                String normalized = "%" + queryText.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), normalized),
                        cb.like(cb.lower(root.get("description")), normalized),
                        cb.like(cb.lower(root.get("referenceCode")), normalized),
                        cb.like(cb.lower(root.get("addressNeighborhood")), normalized),
                        cb.like(cb.lower(root.get("addressCity")), normalized)
                ));
            }
            if (latitude != null && longitude != null && radiusKm != null && radiusKm.signum() > 0) {
                BigDecimal[] bounds = buildGeoBounds(latitude, longitude, radiusKm);
                predicates.add(cb.between(root.get("lat"), bounds[0], bounds[1]));
                predicates.add(cb.between(root.get("lng"), bounds[2], bounds[3]));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static BigDecimal[] buildGeoBounds(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        double radius = radiusKm.doubleValue();

        double deltaLat = radius / 111.32d;
        double cosLat = Math.cos(Math.toRadians(lat));
        double safeCosLat = Math.max(Math.abs(cosLat), 0.01d);
        double deltaLng = radius / (111.32d * safeCosLat);

        return new BigDecimal[] {
                BigDecimal.valueOf(lat - deltaLat),
                BigDecimal.valueOf(lat + deltaLat),
                BigDecimal.valueOf(lng - deltaLng),
                BigDecimal.valueOf(lng + deltaLng)
        };
    }
}
