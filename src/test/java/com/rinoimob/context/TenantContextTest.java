package com.rinoimob.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSetAndGetTenantId() {
        String tenantId = "test-tenant-123";
        TenantContext.setTenantId(tenantId);

        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void testGetTenantIdWhenNotSet() {
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testClearTenantId() {
        TenantContext.setTenantId("test-tenant-123");
        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void testMultipleTenantContexts() {
        TenantContext.setTenantId("tenant-1");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-1");

        TenantContext.setTenantId("tenant-2");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-2");
    }

}
