package com.rinoimob.service.integration;

import com.rinoimob.domain.dto.integration.GrupoOlxFeedStatusResponse;
import com.rinoimob.domain.entity.IntegrationConnection;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.TenantWebsiteConfig;
import com.rinoimob.domain.enums.BillingFeature;
import com.rinoimob.domain.enums.IntegrationConnectionStatus;
import com.rinoimob.domain.enums.IntegrationProvider;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.IntegrationConnectionRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantWebsiteConfigRepository;
import com.rinoimob.service.billing.TenantPlanAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GrupoOlxPropertyFeedService {
    private static final String VRSYNC_NAMESPACE = "http://www.vivareal.com/schemas/1.0/VRSync";
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final Map<String, String> STATE_CODES = stateCodes();

    private final IntegrationConnectionRepository connectionRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantWebsiteConfigRepository websiteConfigRepository;
    private final TenantPlanAccessService planAccessService;

    @Value("${app.public-api-url:http://localhost:39000}")
    private String publicApiUrl;

    @Value("${app.tenant-base-domain:}")
    private String tenantBaseDomain;

    @Transactional(readOnly = true)
    public byte[] generate(String outboundKey) {
        IntegrationConnection connection = connectionRepository
                .findByOutboundKeyAndProvider(outboundKey, IntegrationProvider.GRUPO_OLX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property feed not found"));
        if (connection.getStatus() != IntegrationConnectionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Property feed is paused");
        }
        planAccessService.requireEnabled(connection.getTenantId(), BillingFeature.INTEGRATIONS);
        FeedContext context = loadContext(connection);
        List<String> warnings = contactWarnings(context);
        if (!warnings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("; ", warnings));
        }
        return writeFeed(context);
    }

    @Transactional(readOnly = true)
    public GrupoOlxFeedStatusResponse status(UUID tenantId, UUID connectionId) {
        planAccessService.requireEnabled(tenantId, BillingFeature.INTEGRATIONS);
        IntegrationConnection connection = connectionRepository.findByIdAndTenantId(connectionId, tenantId)
                .filter(item -> item.getProvider() == IntegrationProvider.GRUPO_OLX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo OLX connection not found"));
        FeedContext context = loadContext(connection);
        Map<String, Integer> skipped = new LinkedHashMap<>();
        int published = 0;
        for (Property property : context.properties()) {
            String reason = ineligibilityReason(property);
            if (reason == null) published++;
            else skipped.merge(reason, 1, Integer::sum);
        }
        List<String> warnings = contactWarnings(context);
        if (published == 0) {
            warnings.add("Nenhum imóvel ativo atende aos campos mínimos do feed VRSync.");
        }
        if (connection.getStatus() != IntegrationConnectionStatus.ACTIVE) {
            warnings.add("A conexão está pausada; o feed público responderá como indisponível.");
        }
        String feedUrl = publicApiUrl.replaceAll("/$", "") + "/api/v1/feeds/grupo-olx/"
                + connection.getOutboundKey() + ".xml";
        return new GrupoOlxFeedStatusResponse(feedUrl, warnings.isEmpty(), context.properties().size(), published,
                skipped, List.copyOf(warnings));
    }

    private FeedContext loadContext(IntegrationConnection connection) {
        Tenant tenant = tenantRepository.findById(connection.getTenantId())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantWebsiteConfig website = websiteConfigRepository.findById(connection.getTenantId()).orElse(null);
        List<Property> properties = propertyRepository.findActiveForPortalFeed(connection.getTenantId());
        return new FeedContext(connection, tenant, website, properties);
    }

    private List<String> contactWarnings(FeedContext context) {
        List<String> warnings = new ArrayList<>();
        TenantWebsiteConfig website = context.website();
        if (website == null || blank(website.getEmail())) {
            warnings.add("Configure o e-mail público da imobiliária antes de publicar o feed.");
        }
        if (website == null || (blank(website.getPhone()) && blank(website.getWhatsappNumber()))) {
            warnings.add("Configure o telefone ou WhatsApp público da imobiliária antes de publicar o feed.");
        }
        return warnings;
    }

    private byte[] writeFeed(FeedContext context) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("ListingDataFeed");
            xml.writeDefaultNamespace(VRSYNC_NAMESPACE);
            xml.writeNamespace("xsi", XSI_NAMESPACE);
            xml.writeAttribute(XSI_NAMESPACE, "schemaLocation", VRSYNC_NAMESPACE + " http://xml.vivareal.com/vrsync.xsd");
            writeHeader(xml, context);
            xml.writeStartElement("Listings");
            for (Property property : context.properties()) {
                if (ineligibilityReason(property) == null) writeListing(xml, context, property);
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toByteArray();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Could not generate Grupo OLX property feed", exception);
        }
    }

    private void writeHeader(XMLStreamWriter xml, FeedContext context) throws XMLStreamException {
        TenantWebsiteConfig website = context.website();
        xml.writeStartElement("Header");
        element(xml, "Provider", "Rinoimob");
        element(xml, "Email", website.getEmail());
        element(xml, "ContactName", contactName(context));
        element(xml, "PublishDate", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        element(xml, "Telephone", contactPhone(website));
        xml.writeEndElement();
    }

    private void writeListing(XMLStreamWriter xml, FeedContext context, Property property) throws XMLStreamException {
        xml.writeStartElement("Listing");
        element(xml, "ListingID", property.getReferenceCode().trim());
        element(xml, "Title", truncate(property.getTitle().trim(), 100));
        element(xml, "TransactionType", transactionType(property.getOperation()));
        element(xml, "PublicationType", "STANDARD");
        String detailUrl = detailUrl(context, property);
        if (detailUrl != null) element(xml, "DetailViewUrl", detailUrl);
        writeMedia(xml, property);
        writeDetails(xml, property);
        writeLocation(xml, property);
        writeContact(xml, context);
        xml.writeEndElement();
    }

    private void writeMedia(XMLStreamWriter xml, Property property) throws XMLStreamException {
        xml.writeStartElement("Media");
        List<PropertyPhoto> photos = property.getPhotos().stream().filter(photo -> !blank(photo.getUrl())).toList();
        int primaryIndex = 0;
        for (int index = 0; index < photos.size(); index++) {
            if (Boolean.TRUE.equals(photos.get(index).getIsCover())) {
                primaryIndex = index;
                break;
            }
        }
        for (int index = 0; index < photos.size(); index++) {
            PropertyPhoto photo = photos.get(index);
            xml.writeStartElement("Item");
            xml.writeAttribute("medium", "image");
            xml.writeAttribute("caption", "img" + (index + 1));
            if (index == primaryIndex) xml.writeAttribute("primary", "true");
            xml.writeCharacters(photo.getUrl().trim());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void writeDetails(XMLStreamWriter xml, Property property) throws XMLStreamException {
        xml.writeStartElement("Details");
        element(xml, "UsageType", property.getPropertyType() == PropertyType.COMMERCIAL ? "Commercial" : "Residential");
        element(xml, "PropertyType", propertyType(property.getPropertyType()));
        if (!blank(property.getDescription())) element(xml, "Description", property.getDescription().trim());
        String priceElement = property.getOperation() == PropertyOperation.SALE ? "ListPrice" : "RentalPrice";
        xml.writeStartElement(priceElement);
        xml.writeAttribute("currency", "BRL");
        if (property.getOperation() != PropertyOperation.SALE) {
            xml.writeAttribute("period", property.getOperation() == PropertyOperation.SEASONAL ? "Daily" : "Monthly");
        }
        xml.writeCharacters(integer(property.getPrice()));
        xml.writeEndElement();
        money(xml, "PropertyAdministrationFee", property.getCondoFee());
        money(xml, "YearlyTax", property.getTaxes());
        area(xml, "LotArea", property.getAreaTotal());
        area(xml, "LivingArea", property.getAreaUseful());
        positiveInteger(xml, "Bedrooms", property.getBedrooms());
        positiveInteger(xml, "Bathrooms", property.getBathrooms());
        positiveInteger(xml, "Suites", property.getSuites());
        positiveInteger(xml, "Garage", property.getParking());
        positiveInteger(xml, "UnitFloor", property.getFloorNumber());
        xml.writeEndElement();
    }

    private void writeLocation(XMLStreamWriter xml, Property property) throws XMLStreamException {
        xml.writeStartElement("Location");
        xml.writeAttribute("displayAddress", "Neighborhood");
        xml.writeStartElement("Country");
        xml.writeAttribute("abbreviation", "BR");
        xml.writeCharacters("Brasil");
        xml.writeEndElement();
        xml.writeStartElement("State");
        xml.writeAttribute("abbreviation", stateCode(property.getAddressState()));
        xml.writeCharacters(property.getAddressState().trim());
        xml.writeEndElement();
        element(xml, "City", property.getAddressCity().trim());
        element(xml, "Neighborhood", property.getAddressNeighborhood().trim());
        if (!blank(property.getAddressZip())) element(xml, "PostalCode", property.getAddressZip().trim());
        if (property.getLat() != null) element(xml, "Latitude", property.getLat().toPlainString());
        if (property.getLng() != null) element(xml, "Longitude", property.getLng().toPlainString());
        xml.writeEndElement();
    }

    private void writeContact(XMLStreamWriter xml, FeedContext context) throws XMLStreamException {
        TenantWebsiteConfig website = context.website();
        xml.writeStartElement("ContactInfo");
        element(xml, "Name", contactName(context));
        element(xml, "Email", website.getEmail().trim());
        String websiteUrl = websiteUrl(context);
        if (websiteUrl != null) element(xml, "Website", websiteUrl);
        if (!blank(website.getLogoUrl())) element(xml, "Logo", website.getLogoUrl().trim());
        element(xml, "Telephone", contactPhone(website));
        xml.writeEndElement();
    }

    private String ineligibilityReason(Property property) {
        if (blank(property.getReferenceCode()) || property.getReferenceCode().trim().length() > 50) return "Referência ausente ou maior que 50 caracteres";
        if (blank(property.getTitle()) || property.getTitle().trim().length() < 10) return "Título com menos de 10 caracteres";
        if (property.getPrice() == null || property.getPrice().compareTo(BigDecimal.ZERO) <= 0) return "Preço não informado";
        if (blank(property.getAddressState()) || blank(property.getAddressCity()) || blank(property.getAddressNeighborhood())) return "Localização incompleta";
        if (property.getPhotos().stream().noneMatch(photo -> !blank(photo.getUrl()))) return "Sem foto pública";
        return null;
    }

    private String detailUrl(FeedContext context, Property property) {
        String base = websiteUrl(context);
        return base == null ? null : base + "/imoveis/" + property.getId();
    }

    private String websiteUrl(FeedContext context) {
        if (context.website() != null && !blank(context.website().getCustomDomain())) {
            return "https://" + context.website().getCustomDomain().trim().replaceAll("^https?://", "").replaceAll("/$", "");
        }
        if (!blank(tenantBaseDomain)) {
            return "https://" + context.tenant().getSubdomain() + "." + tenantBaseDomain.trim().replaceAll("^https?://", "").replaceAll("/$", "");
        }
        return null;
    }

    private String contactName(FeedContext context) {
        return context.website() != null && !blank(context.website().getCompanyName())
                ? context.website().getCompanyName().trim() : context.tenant().getName();
    }

    private String contactPhone(TenantWebsiteConfig website) {
        return !blank(website.getPhone()) ? website.getPhone().trim() : website.getWhatsappNumber().trim();
    }

    private void money(XMLStreamWriter xml, String name, BigDecimal value) throws XMLStreamException {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) return;
        xml.writeStartElement(name);
        xml.writeAttribute("currency", "BRL");
        xml.writeCharacters(integer(value));
        xml.writeEndElement();
    }

    private void area(XMLStreamWriter xml, String name, BigDecimal value) throws XMLStreamException {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return;
        xml.writeStartElement(name);
        xml.writeAttribute("unit", "square metres");
        xml.writeCharacters(integer(value));
        xml.writeEndElement();
    }

    private void positiveInteger(XMLStreamWriter xml, String name, Integer value) throws XMLStreamException {
        if (value != null && value > 0) element(xml, name, value.toString());
    }

    private void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private String transactionType(PropertyOperation operation) {
        return operation == PropertyOperation.SALE ? "For Sale" : "For Rent";
    }

    private String propertyType(PropertyType type) {
        return switch (type) {
            case HOUSE -> "Residential / Home";
            case APARTMENT -> "Residential / Apartment";
            case LAND -> "Residential / Land Lot";
            case COMMERCIAL -> "Commercial / Business";
            case RURAL -> "Residential / Farm Ranch";
        };
    }

    private String stateCode(String state) {
        String value = state.trim().toUpperCase(Locale.ROOT);
        if (value.length() == 2) return value;
        return STATE_CODES.getOrDefault(normalize(value), value.substring(0, Math.min(2, value.length())));
    }

    private String integer(BigDecimal value) {
        return value.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static Map<String, String> stateCodes() {
        Map<String, String> values = new LinkedHashMap<>();
        String[][] states = {{"ACRE", "AC"}, {"ALAGOAS", "AL"}, {"AMAPA", "AP"}, {"AMAZONAS", "AM"},
                {"BAHIA", "BA"}, {"CEARA", "CE"}, {"DISTRITO FEDERAL", "DF"}, {"ESPIRITO SANTO", "ES"},
                {"GOIAS", "GO"}, {"MARANHAO", "MA"}, {"MATO GROSSO", "MT"}, {"MATO GROSSO DO SUL", "MS"},
                {"MINAS GERAIS", "MG"}, {"PARA", "PA"}, {"PARAIBA", "PB"}, {"PARANA", "PR"},
                {"PERNAMBUCO", "PE"}, {"PIAUI", "PI"}, {"RIO DE JANEIRO", "RJ"}, {"RIO GRANDE DO NORTE", "RN"},
                {"RIO GRANDE DO SUL", "RS"}, {"RONDONIA", "RO"}, {"RORAIMA", "RR"}, {"SANTA CATARINA", "SC"},
                {"SAO PAULO", "SP"}, {"SERGIPE", "SE"}, {"TOCANTINS", "TO"}};
        for (String[] state : states) values.put(state[0], state[1]);
        return Map.copyOf(values);
    }

    private record FeedContext(IntegrationConnection connection, Tenant tenant, TenantWebsiteConfig website,
                               List<Property> properties) {}
}
