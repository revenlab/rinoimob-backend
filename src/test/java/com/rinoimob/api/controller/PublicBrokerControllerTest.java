package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateLeadRequest;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantWebsiteConfigRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.crm.LeadService;
import com.rinoimob.service.imoveis.PropertyService;
import com.rinoimob.service.imoveis.PropertyTypeService;
import com.rinoimob.service.website.BlogPostService;
import com.rinoimob.service.website.TenantWebsiteConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicBrokerControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID brokerId = UUID.randomUUID();
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PropertyService properties = mock(PropertyService.class);
    private final LeadService leads = mock(LeadService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        when(tenants.findBySubdomain("tenant-a")).thenReturn(Optional.of(tenant));
        PublicController controller = new PublicController(tenants, mock(TenantWebsiteConfigRepository.class),
                users, properties, leads, mock(TenantWebsiteConfigService.class),
                mock(BlogPostService.class), mock(PropertyTypeService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnCommercialProfileWithoutInternalIdentifiers() throws Exception {
        User broker = broker();
        when(users.findByTenantIdAndPublicSlugAndActive(tenantId, "ana", true)).thenReturn(Optional.of(broker));
        mvc.perform(get("/api/v1/public/brokers/ANA").header("X-Tenant-Slug", "tenant-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ana Broker"))
                .andExpect(jsonPath("$.slug").value("ana"))
                .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void shouldRejectBrokerFromAnotherTenant() throws Exception {
        mvc.perform(get("/api/v1/public/brokers/ana").header("X-Tenant-Slug", "tenant-a"))
                .andExpect(status().isNotFound());
        verify(users).findByTenantIdAndPublicSlugAndActive(tenantId, "ana", true);
    }

    @Test
    void shouldPreserveBrokerReferenceWhenCreatingPublicLead() throws Exception {
        when(users.findByTenantIdAndPublicSlugAndActive(tenantId, "ana", true)).thenReturn(Optional.of(broker()));
        mvc.perform(post("/api/v1/public/leads").header("X-Tenant-Slug", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente\",\"brokerSlug\":\" ANA \"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateLeadRequest> request = ArgumentCaptor.forClass(CreateLeadRequest.class);
        verify(leads).create(eq(tenantId), request.capture());
        assertThat(request.getValue().referredByUserId()).isEqualTo(brokerId);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldPreserveGenericRoutingWhenReferenceIsAbsent() throws Exception {
        mvc.perform(post("/api/v1/public/leads").header("X-Tenant-Slug", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateLeadRequest> request = ArgumentCaptor.forClass(CreateLeadRequest.class);
        verify(leads).create(eq(tenantId), request.capture());
        assertThat(request.getValue().referredByUserId()).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void shouldRejectForeignBrokerReferenceWithoutCreatingLead() throws Exception {
        mvc.perform(post("/api/v1/public/leads").header("X-Tenant-Slug", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cliente\",\"brokerSlug\":\"foreign\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(leads);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldScopeCatalogAndClearContextAfterRequest() throws Exception {
        when(users.findByTenantIdAndPublicSlugAndActive(tenantId, "ana", true)).thenReturn(Optional.of(broker()));
        when(properties.listPublicByBroker(eq(brokerId), any())).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId.toString());
            return Page.empty(invocation.getArgument(1));
        });
        mvc.perform(get("/api/v1/public/brokers/ana/properties").header("X-Tenant-Slug", "tenant-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldRejectUnboundedCatalogPages() throws Exception {
        when(users.findByTenantIdAndPublicSlugAndActive(tenantId, "ana", true)).thenReturn(Optional.of(broker()));
        mvc.perform(get("/api/v1/public/brokers/ana/properties").param("size", "10000")
                        .header("X-Tenant-Slug", "tenant-a"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(properties);
    }

    private User broker() {
        User user = new User();
        user.setId(brokerId);
        user.setTenantId(tenantId);
        user.setPublicSlug("ana");
        user.setFirstName("Ana");
        user.setLastName("Broker");
        return user;
    }
}
