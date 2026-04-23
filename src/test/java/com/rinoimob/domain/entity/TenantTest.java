package com.rinoimob.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTest {

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
    }

    @Test
    void testTenantCreationWithBasicFields() {
        tenant.setId("tenant-123");
        tenant.setName("Test Tenant");
        tenant.setSubdomain("test");
        tenant.setActive(true);

        assertThat(tenant.getId()).isEqualTo("tenant-123");
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
