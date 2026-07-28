package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PropertySpecification {

    private static final String ACCENTED_CHARACTERS = "áàâãäåéèêëíìîïóòôõöúùûüçýÿ";
    private static final String UNACCENTED_CHARACTERS = "aaaaaaeeeeiiiiooooouuuucyy";

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
            BigDecimal radiusKm,
            Boolean featured) {

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
            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
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
                        accentInsensitive(root.get("addressCity"), cb),
                        containsNormalized(city)
                ));
            }
            if (queryText != null && !queryText.isBlank()) {
                String normalized = containsNormalized(queryText);
                predicates.add(cb.or(
                        cb.like(accentInsensitive(root.get("title"), cb), normalized),
                        cb.like(accentInsensitive(root.get("description"), cb), normalized),
                        cb.like(accentInsensitive(root.get("referenceCode"), cb), normalized),
                        cb.like(accentInsensitive(root.get("addressNeighborhood"), cb), normalized),
                        cb.like(accentInsensitive(root.get("addressCity"), cb), normalized)
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

    private static Expression<String> accentInsensitive(Expression<String> expression,
                                                        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.function(
                "translate",
                String.class,
                cb.lower(expression),
                cb.literal(ACCENTED_CHARACTERS),
                cb.literal(UNACCENTED_CHARACTERS)
        );
    }

    private static String containsNormalized(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return "%" + normalized + "%";
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
