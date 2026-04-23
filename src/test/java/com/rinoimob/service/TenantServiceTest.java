package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private TenantRepository tenantRepository;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreateTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Test Tenant");
        tenant.setSubdomain("test");

        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        Tenant result = tenantService.createTenant("Test Tenant", "test");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Tenant");
        assertThat(result.getSubdomain()).isEqualTo("test");
        verify(tenantRepository, times(1)).save(any(Tenant.class));
    }

    @Test
    void testGetTenantById() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Test Tenant");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Optional<Tenant> result = tenantService.getTenantById(TENANT_ID.toString());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Tenant");
    }

    @Test
    void testGetTenantBySubdomain() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setSubdomain("test");

        when(tenantRepository.findBySubdomain("test")).thenReturn(Optional.of(tenant));

        Optional<Tenant> result = tenantService.getTenantBySubdomain("test");

        assertThat(result).isPresent();
        assertThat(result.get().getSubdomain()).isEqualTo("test");
    }

    @Test
    void testGetCurrentTenantId() {
        TenantContext.setTenantId("current-tenant-123");

        String tenantId = tenantService.getCurrentTenantId();

        assertThat(tenantId).isEqualTo("current-tenant-123");
    }

    @Test
    void testGetCurrentTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Current Tenant");

        TenantContext.setTenantId(TENANT_ID.toString());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        Optional<Tenant> result = tenantService.getCurrentTenant();

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Current Tenant");
    }

    @Test
    void testUpdateTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Old Name");

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        Tenant result = tenantService.updateTenant(TENANT_ID.toString(), "New Name", "new-subdomain");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getSubdomain()).isEqualTo("new-subdomain");
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    void testDeactivateTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setActive(true);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

        tenantService.deactivateTenant(TENANT_ID.toString());

        assertThat(tenant.getActive()).isFalse();
        verify(tenantRepository, times(1)).save(tenant);
    }

    @Test
    void testUpdateTenantNotFound() {
        when(tenantRepository.findById(UNKNOWN_TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.updateTenant(UNKNOWN_TENANT_ID.toString(), "Name", "subdomain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant not found");
    }

}
