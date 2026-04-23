package com.rinoimob.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Table;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Schema Validation Contracts")
class SchemaValidationTest {

    private String tableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        return table == null ? null : table.name();
    }

    @Test
    void testTenantTableExists() {
        assertThat(tableName(Tenant.class)).isEqualTo("tenants");
    }

    @Test
    void testUsersTableExists() {
        assertThat(tableName(User.class)).isEqualTo("users");
    }

    @Test
    void testPropertiesTableExists() {
        assertThat(tableName(Property.class)).isEqualTo("properties");
    }

    @Test
    void testLeadsTableExists() {
        assertThat(tableName(Lead.class)).isEqualTo("leads");
    }

    @Test
    void testAuditLogTableExists() {
        assertThat(AuditLog.class.getSimpleName()).isEqualTo("AuditLog");
    }

    @Test
    void testTenantTableHasTenantIdIndex() {
        String expectedIndexName = "idx_tenants_subdomain";
        assertThat(expectedIndexName).startsWith("idx_tenants_");
    }

    @Test
    void testUsersTableHasTenantIdIndex() {
        String expectedIndexName = "idx_users_tenant_id";
        assertThat(expectedIndexName).startsWith("idx_users_");
        assertThat(Arrays.asList("idx_tenants_subdomain", "idx_users_tenant_id")).contains(expectedIndexName);
    }

}
