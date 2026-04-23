package com.rinoimob.integration;

import com.rinoimob.controller.TenantController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Tenant Service Integration Tests")
class TenantServiceIntegrationTest {

    private MockMvc mockMvc;

    private final String baseUrl = "/api/v1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TenantController()).build();
    }

    @Test
    @DisplayName("Should retrieve all tenants")
    void testGetAllTenants() throws Exception {
        MvcResult result = mockMvc.perform(get(baseUrl + "/tenants"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isNotEmpty();
    }

    @Test
    @DisplayName("Should retrieve tenant by ID")
    void testGetTenantById() throws Exception {
        String tenantId = "tenant-123";

        MvcResult result = mockMvc.perform(get(baseUrl + "/tenants/" + tenantId))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(tenantId);
    }

    @Test
    @DisplayName("Should create a new tenant")
    void testCreateTenant() throws Exception {
        String tenantRequest = "{\"name\": \"Test Tenant\"}";

        MvcResult result = mockMvc.perform(post(baseUrl + "/tenants")
                .contentType("application/json")
                .content(tenantRequest))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("tenant-");
    }
}
