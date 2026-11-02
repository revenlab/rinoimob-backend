package com.rinoimob.service.website;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.UpdateTenantWebsiteConfigRequest;
import com.rinoimob.domain.entity.TenantWebsiteConfig;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantWebsiteConfigRepository;
import com.rinoimob.service.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantWebsiteConfigServiceTest {

    @Mock private TenantWebsiteConfigRepository configRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private FileStorageService storageService;
    @Mock private CloudflareCustomHostnameService hostnameService;

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private TenantWebsiteConfigService service;

    @BeforeEach
    void setUp() {
        service = new TenantWebsiteConfigService(configRepository, tenantRepository, storageService, hostnameService);
    }

    @Test
    void shouldPersistOrderedServicesAndReadThemBackOnlyForTheirTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantWebsiteConfig configA = new TenantWebsiteConfig();
        configA.setTenantId(tenantA);
        TenantWebsiteConfig configB = new TenantWebsiteConfig();
        configB.setTenantId(tenantB);
        String items = """
                [{"title":"Financiamento","description":"Crédito","icon":"🔑","imageUrl":""},
                 {"title":"Assessoria","description":"Contratos","icon":"","imageUrl":"https://example.com/icon.png"}]
                """;
        when(tenantRepository.existsById(any())).thenReturn(true);
        when(configRepository.findById(tenantA)).thenReturn(Optional.of(configA));
        when(configRepository.findById(tenantB)).thenReturn(Optional.of(configB));
        when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateConfig(tenantA, request(Map.of("servicesItems", items)));

        assertThat(mapper.valueToTree(service.getConfig(tenantA)).path("servicesItems").asText()).isEqualTo(items);
        assertThat(mapper.valueToTree(service.getConfig(tenantB)).path("servicesItems").isNull()).isTrue();

        service.updateConfig(tenantA, request(Map.of("companyName", "Imobiliária")));
        assertThat(mapper.valueToTree(service.getConfig(tenantA)).path("servicesItems").asText()).isEqualTo(items);

        service.updateConfig(tenantA, request(Map.of("servicesItems", "[]")));
        assertThat(mapper.valueToTree(service.getConfig(tenantA)).path("servicesItems").asText()).isEqualTo("[]");
    }

    private UpdateTenantWebsiteConfigRequest request(Map<String, Object> values) {
        return mapper.convertValue(values, UpdateTenantWebsiteConfigRequest.class);
    }
}
