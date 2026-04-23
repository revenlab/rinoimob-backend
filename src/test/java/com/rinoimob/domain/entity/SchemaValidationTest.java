package com.rinoimob.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SchemaValidationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void testTenantTableExists() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='tenants'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testUsersTableExists() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='users'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testPropertiesTableExists() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='properties'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testLeadsTableExists() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='leads'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testAuditLogTableExists() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name='audit_log'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testTenantTableHasTenantIdIndex() {
        String sql = "SELECT indexname FROM pg_indexes WHERE tablename='tenants' AND indexname='idx_tenants_subdomain'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

    @Test
    void testUsersTableHasTenantIdIndex() {
        String sql = "SELECT indexname FROM pg_indexes WHERE tablename='users' AND indexname='idx_users_tenant_id'";
        Object result = entityManager.createNativeQuery(sql).getSingleResult();
        assertThat(result).isNotNull();
    }

}
