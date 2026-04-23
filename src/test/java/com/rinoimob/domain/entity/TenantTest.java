package com.rinoimob.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTest {

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
    }

    @Test
    void testTenantCreationWithBasicFields() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        tenant.setId(tenantId);
        tenant.setName("Test Tenant");
        tenant.setSubdomain("test");
        tenant.setActive(true);

        assertThat(tenant.getId()).isEqualTo(tenantId);
        assertThat(tenant.getName()).isEqualTo("Test Tenant");
        assertThat(tenant.getSubdomain()).isEqualTo("test");
        assertThat(tenant.getActive()).isTrue();
    }

    @Test
    void testTenantOnCreate() {
        tenant.setName("Test Tenant");
        tenant.setSubdomain("test");
        tenant.onCreate();

        assertThat(tenant.getCreatedAt()).isNotNull();
        assertThat(tenant.getUpdatedAt()).isNotNull();
    }

    @Test
    void testTenantOnUpdate() {
        tenant.setName("Test Tenant");
        tenant.onCreate();
        
        tenant.setName("Updated Tenant");
        tenant.onUpdate();

        assertThat(tenant.getUpdatedAt()).isNotNull();
    }

    @Test
    void testTenantDefaultActive() {
        tenant.setName("Test Tenant");
        tenant.setSubdomain("test");

        assertThat(tenant.getActive()).isTrue();
    }

}
