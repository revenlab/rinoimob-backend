package com.rinoimob.domain.repository;

import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PublicBrokerCatalogTest {
    @Autowired private TestEntityManager em;
    @Autowired private PropertyRepository properties;

    @Test
    void shouldListOnlyActiveOfferedPropertiesWithinTenantWithoutDuplicateCounts() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        User ana = broker(tenantA, "ana@example.test");
        User other = broker(tenantA, "other@example.test");
        Property shared = property(tenantA, "Shared", PropertyStatus.ACTIVE, true);
        shared.setBrokers(Set.of(ana, other));
        property(tenantA, "Assigned", PropertyStatus.ACTIVE, false).setBrokers(Set.of(ana));
        property(tenantA, "Other broker", PropertyStatus.ACTIVE, false).setBrokers(Set.of(other));
        property(tenantA, "Draft", PropertyStatus.DRAFT, true);
        property(tenantA, "Deleted", PropertyStatus.ACTIVE, true).setDeletedAt(LocalDateTime.now());
        property(tenantB, "Foreign", PropertyStatus.ACTIVE, true);
        em.flush();
        // Execute the predicates/count against flushed rows; retain managed fixtures
        // because H2 does not implement PostgreSQL's JSONB JDBC hydration.
        // This test covers catalog filtering/counting, not JSONB serialization.

        var page = properties.findPublicByBroker(tenantA, ana.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        var all = properties.findPublicByBroker(tenantA, ana.getId(), PageRequest.of(0, 20));
        assertThat(all.getContent()).extracting(Property::getTitle).containsExactlyInAnyOrder("Shared", "Assigned");
        assertThat(all.getContent()).extracting(Property::getTenantId).containsOnly(tenantA);
    }

    private User broker(UUID tenantId, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        return em.persist(user);
    }

    private Property property(UUID tenantId, String title, PropertyStatus status, boolean availableToAll) {
        Property property = new Property();
        property.setTenantId(tenantId);
        property.setTitle(title);
        property.setSlug(UUID.randomUUID().toString());
        property.setOperation(PropertyOperation.SALE);
        property.setPropertyType(PropertyType.HOUSE);
        property.setStatus(status);
        property.setAvailableToAllBrokers(availableToAll);
        return em.persist(property);
    }
}
