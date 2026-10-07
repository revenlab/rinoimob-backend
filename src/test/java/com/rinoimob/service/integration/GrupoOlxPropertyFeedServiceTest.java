package com.rinoimob.service.integration;

import com.rinoimob.domain.dto.integration.GrupoOlxFeedStatusResponse;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantWebsiteConfig;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantWebsiteConfigRepository;
import com.rinoimob.service.billing.TenantPlanAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrupoOlxPropertyFeedServiceTest {
    @Mock
    private IntegrationConnectionRepository connectionRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantWebsiteConfigRepository websiteConfigRepository;
    @Mock
    private TenantPlanAccessService planAccessService;

    private GrupoOlxPropertyFeedService service;
    private UUID tenantId;
    private UUID connectionId;
    private IntegrationConnection connection;
    private Tenant tenant;
    private TenantWebsiteConfig website;

    @BeforeEach
    void setUp() {
        service = new GrupoOlxPropertyFeedService(connectionRepository, propertyRepository, tenantRepository,
                websiteConfigRepository, planAccessService);
        ReflectionTestUtils.setField(service, "publicApiUrl", "https://api.rinoimob.com.br");
        ReflectionTestUtils.setField(service, "tenantBaseDomain", "rinoimob.com.br");

        tenantId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        connection = new IntegrationConnection();
        connection.setId(connectionId);
        connection.setTenantId(tenantId);
        connection.setProvider(IntegrationProvider.GRUPO_OLX);
        connection.setStatus(IntegrationConnectionStatus.ACTIVE);
        connection.setOutboundKey("feed-key");

        tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Imobiliária Horizonte");
        tenant.setSubdomain("horizonte");
        tenant.setActive(true);

        website = new TenantWebsiteConfig();
        website.setTenantId(tenantId);
        website.setCompanyName("Imobiliária Horizonte");
        website.setEmail("contato@horizonte.com.br");
        website.setPhone("(11) 3333-4444");
        website.setLogoUrl("https://cdn.example.com/logo.png");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(websiteConfigRepository.findById(tenantId)).thenReturn(Optional.of(website));
    }

    @Test
    void shouldGenerateVrsyncFeedWithOnlyEligibleProperties() {
        Property eligible = property("IMV-100", "Apartamento amplo no centro", new BigDecimal("850000.90"));
        Property invalid = property("IMV-101", "Casa sem foto cadastrada", new BigDecimal("400000"));
        invalid.setPhotos(List.of());
        when(connectionRepository.findByOutboundKeyAndProvider("feed-key", IntegrationProvider.GRUPO_OLX))
                .thenReturn(Optional.of(connection));
        when(propertyRepository.findActiveForPortalFeed(tenantId)).thenReturn(List.of(eligible, invalid));

        String xml = new String(service.generate("feed-key"), StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("<ListingDataFeed")
                .contains("http://www.vivareal.com/schemas/1.0/VRSync")
                .contains("<ListingID>IMV-100</ListingID>")
                .contains("<TransactionType>For Sale</TransactionType>")
                .contains("<ListPrice currency=\"BRL\">850000</ListPrice>")
                .contains("<State abbreviation=\"SP\">São Paulo</State>")
                .contains("<DetailViewUrl>https://horizonte.rinoimob.com.br/imoveis/" + eligible.getId())
                .doesNotContain("IMV-101");
    }

    @Test
    void shouldReportSkippedPropertiesAndMissingContactConfiguration() {
        website.setEmail(null);
        website.setPhone(null);
        website.setWhatsappNumber(null);
        Property invalid = property("IMV-200", "Terreno sem preço cadastrado", null);
        when(connectionRepository.findByIdAndTenantId(connectionId, tenantId)).thenReturn(Optional.of(connection));
        when(propertyRepository.findActiveForPortalFeed(tenantId)).thenReturn(List.of(invalid));

        GrupoOlxFeedStatusResponse status = service.status(tenantId, connectionId);

        assertThat(status.ready()).isFalse();
        assertThat(status.activeProperties()).isEqualTo(1);
        assertThat(status.publishedProperties()).isZero();
        assertThat(status.skippedProperties()).containsEntry("Preço não informado", 1);
        assertThat(status.warnings()).anyMatch(message -> message.contains("e-mail público"));
        assertThat(status.warnings()).anyMatch(message -> message.contains("Nenhum imóvel ativo"));
    }

    private Property property(String reference, String title, BigDecimal price) {
        Property property = new Property();
        property.setId(UUID.randomUUID());
        property.setTenantId(tenantId);
        property.setReferenceCode(reference);
        property.setTitle(title);
        property.setDescription("Imóvel completo, pronto para receber sua família.");
        property.setOperation(PropertyOperation.SALE);
        property.setPropertyType(PropertyType.APARTMENT);
        property.setPrice(price);
        property.setAreaUseful(new BigDecimal("80.9"));
        property.setBedrooms(2);
        property.setBathrooms(1);
        property.setParking(1);
        property.setAddressState("São Paulo");
        property.setAddressCity("São Paulo");
        property.setAddressNeighborhood("Centro");
        PropertyPhoto photo = new PropertyPhoto();
        photo.setUrl("https://cdn.example.com/property.jpg");
        photo.setIsCover(true);
        property.setPhotos(List.of(photo));
        return property;
    }
}
