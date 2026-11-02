package com.rinoimob.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyCreatorMigrationTest {
    @Test
    void shouldPreserveExistingPropertiesWithoutInventingTheirCreator() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:creator_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        UUID propertyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try {
            jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            jdbc.execute("CREATE TABLE properties (id UUID PRIMARY KEY, tenant_id UUID NOT NULL)");
            jdbc.update("INSERT INTO properties VALUES (?, ?)", propertyId, tenantId);

            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V69__property_creator.sql")).execute(dataSource);

            assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM properties WHERE id = ?", UUID.class, propertyId)).isNull();
            assertThat(jdbc.queryForObject("SELECT tenant_id FROM properties WHERE id = ?", UUID.class, propertyId)).isEqualTo(tenantId);
            jdbc.update("INSERT INTO users VALUES (?)", userId);
            jdbc.update("UPDATE properties SET created_by_user_id = ? WHERE id = ?", userId, propertyId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
            assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM properties WHERE id = ?", UUID.class, propertyId)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM properties", Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP ALL OBJECTS");
            jdbc.execute("SHUTDOWN");
        }
    }
}
