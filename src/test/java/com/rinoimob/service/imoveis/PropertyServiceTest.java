package com.rinoimob.service.imoveis;

import com.rinoimob.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.entity.FloorPlan;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.entity.PropertyVideo;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.enums.PropertyCondition;
import com.rinoimob.domain.enums.PropertyVideoSource;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.billing.TenantQuotaEnforcementService;
import com.rinoimob.service.storage.FileStorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROPERTY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PHOTO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FLOOR_PLAN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID VIDEO_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyPhotoRepository photoRepository;
    @Mock private FloorPlanRepository floorPlanRepository;
    @Mock private FloorPlanPhotoRepository floorPlanPhotoRepository;
    @Mock private PropertyVideoRepository videoRepository;
    @Mock private PropertyCategoryRepository categoryRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CategoryService categoryService;
    @Mock private PropertyTypeService propertyTypeService;
    @Mock private TenantQuotaEnforcementService tenantQuotaEnforcementService;
    @Mock private UserRepository userRepository;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID.toString());
        TenantContext.setUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        authenticate("properties:write");
        propertyService = new PropertyService(
                propertyRepository, photoRepository, floorPlanRepository,
                floorPlanPhotoRepository, videoRepository, categoryRepository, fileStorageService, categoryService,
                propertyTypeService, tenantQuotaEnforcementService, userRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "broker", null, List.of(new SimpleGrantedAuthority("PERMISSION_" + permission))));
    }

    @Test
    void shouldRejectOfferingToAllForOwnOnlyWriter() {
        authenticate("properties:write_own");
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa", "operation", "SALE", "propertyType", "HOUSE",
                "availableToAllBrokers", true), CreatePropertyRequest.class);

        lenient().when(propertyRepository.save(any())).thenReturn(buildProperty());
        assertThatThrownBy(() -> propertyService.createProperty(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void shouldRejectEditingPropertyWithoutOwnershipForOwnOnlyWriter() {
        authenticate("properties:write_own");
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(buildProperty()));
        lenient().when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdatePropertyRequest request = new ObjectMapper().convertValue(Map.of("title", "Changed"), UpdatePropertyRequest.class);

        assertThatThrownBy(() -> propertyService.updateProperty(PROPERTY_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectDeletingPropertyWithoutOwnershipForOwnOnlyWriter() {
        authenticate("properties:write_own");
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(buildProperty()));

        assertThatThrownBy(() -> propertyService.deleteProperty(PROPERTY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void shouldCreateOwnPortfolioByDefaultAndRecordCreator() {
        authenticate("properties:write_own");
        UUID actorId = TenantContext.getUserId();
        com.rinoimob.domain.entity.User actor = new com.rinoimob.domain.entity.User();
        actor.setId(actorId);
        actor.setTenantId(TENANT_ID);
        when(userRepository.findByTenantIdAndIdInAndActive(TENANT_ID, Set.of(actorId), true)).thenReturn(List.of(actor));
        when(propertyRepository.save(any())).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            property.setId(PROPERTY_ID);
            return property;
        });
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa própria", "operation", "SALE", "propertyType", "HOUSE"), CreatePropertyRequest.class);

        PropertyResponse result = propertyService.createProperty(request);

        assertThat(result.availableToAllBrokers()).isFalse();
        assertThat(result.brokerIds()).containsExactly(actorId);
        ArgumentCaptor<Property> saved = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(saved.capture());
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(actorId);
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void shouldKeepLegacyAllBrokerDefaultWhenOfferingFieldsAreAbsent() {
        when(propertyRepository.save(any())).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            property.setId(PROPERTY_ID);
            return property;
        });
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa", "operation", "SALE", "propertyType", "HOUSE"), CreatePropertyRequest.class);

        assertThat(propertyService.createProperty(request).availableToAllBrokers()).isTrue();
    }

    @Test
    void shouldAllowOwnWriterToEditOwnProperty() {
        authenticate("properties:write_own");
        Property property = buildProperty();
        property.setCreatedByUserId(TenantContext.getUserId());
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdatePropertyRequest request = new ObjectMapper().convertValue(Map.of("title", "Novo título"), UpdatePropertyRequest.class);

        assertThat(propertyService.updateProperty(PROPERTY_ID, request).title()).isEqualTo("Novo título");
        assertThat(propertyService.canEditProperty(PROPERTY_ID)).isTrue();
        TenantContext.setUserId(UUID.randomUUID());
        assertThat(propertyService.canEditProperty(PROPERTY_ID)).isFalse();
    }

    @Test
    void shouldRejectOfferingToAnotherBrokerForOwnOnlyWriter() {
        authenticate("properties:write_own");
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa", "operation", "SALE", "propertyType", "HOUSE",
                "availableToAllBrokers", false, "brokerIds", List.of(UUID.randomUUID())), CreatePropertyRequest.class);

        assertThatThrownBy(() -> propertyService.createProperty(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void shouldRejectMediaChangesBeforeUploadingForAnotherOwner() {
        authenticate("properties:write_own");
        Property property = buildProperty();
        property.setCreatedByUserId(UUID.randomUUID());
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> propertyService.addPhoto(PROPERTY_ID,
                new MockMultipartFile("file", "image.jpg", "image/jpeg", new byte[]{1}), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void shouldRejectInactiveOrForeignBrokersForAllWriter() {
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa", "operation", "SALE", "propertyType", "HOUSE",
                "availableToAllBrokers", false, "brokerIds", List.of(UUID.randomUUID())), CreatePropertyRequest.class);

        assertThatThrownBy(() -> propertyService.createProperty(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void shouldDenyCreateForReadOnlyUser() {
        authenticate("properties:read");
        CreatePropertyRequest request = new ObjectMapper().convertValue(Map.of(
                "title", "Casa", "operation", "SALE", "propertyType", "HOUSE"), CreatePropertyRequest.class);

        assertThatThrownBy(() -> propertyService.createProperty(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void shouldExposePerPropertyWriteAccessOnlyOnAuthenticatedListing() {
        authenticate("properties:write_own");
        Property own = buildProperty();
        own.setCreatedByUserId(TenantContext.getUserId());
        Property other = buildProperty();
        other.setId(UUID.randomUUID());
        other.setCreatedByUserId(UUID.randomUUID());
        when(propertyRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own, other), PageRequest.of(0, 20), 2));
        var controller = new com.rinoimob.api.controller.PropertyController(propertyService);

        var page = controller.list(null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20)).getBody();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(page);

        assertThat(json.path("content").get(0).path("canEdit").asBoolean()).isTrue();
        assertThat(json.path("content").get(1).path("canEdit").asBoolean()).isFalse();
        assertThat(json.path("content").get(0).path("id").asText()).isEqualTo(PROPERTY_ID.toString());
        assertThat(json.path("content").get(0).has("createdByUserId")).isFalse();
    }

    // ── createProperty ────────────────────────────────────────────────────────

    @Test
    void createProperty_savesAndReturnsMappedResponse() {
        CreatePropertyRequest req = new CreatePropertyRequest(
                "Casa Teste", null, "Descrição", PropertyOperation.SALE, PropertyType.HOUSE,
                PropertyStatus.DRAFT, null, null, true, new BigDecimal("500000"), "BRL", null, null,
                new BigDecimal("120"), new BigDecimal("100"), 3, 1, 2, 2, null,
                "Rua A", "10", null, "Bairro", "São Paulo", "SP", "BR", "01001-000",
                null, null, null, null, true, List.of());

        Property savedProperty = buildProperty();
        when(propertyRepository.save(any(Property.class))).thenReturn(savedProperty);

        PropertyResponse response = propertyService.createProperty(req);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(PROPERTY_ID);
        assertThat(response.title()).isEqualTo("Casa Teste");
        verify(propertyRepository).save(argThat(Property::isFeatured));
    }

    @Test
    void createProperty_defaultsToDraftStatus_whenStatusNull() {
        CreatePropertyRequest req = new CreatePropertyRequest(
                "Casa", null, null, PropertyOperation.RENT, PropertyType.APARTMENT,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, true, List.of());

        Property savedProperty = buildProperty();
        when(propertyRepository.save(any(Property.class))).thenReturn(savedProperty);

        propertyService.createProperty(req);

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PropertyStatus.DRAFT);
    }

    // ── getProperty ───────────────────────────────────────────────────────────

    @Test
    void getProperty_returnsResponse_whenFound() {
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(buildProperty()));

        PropertyResponse response = propertyService.getProperty(PROPERTY_ID);

        assertThat(response.id()).isEqualTo(PROPERTY_ID);
    }

    @Test
    void getProperty_throws404_whenNotFound() {
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.getProperty(PROPERTY_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Property not found");
    }

    // ── updateProperty ────────────────────────────────────────────────────────

    @Test
    void updateProperty_updatesOnlyProvidedFields() {
        Property existing = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenReturn(existing);

        UpdatePropertyRequest req = buildUpdateRequest("Novo Título", null);

        PropertyResponse response = propertyService.updateProperty(PROPERTY_ID, req);

        assertThat(response).isNotNull();
        assertThat(existing.getTitle()).isEqualTo("Novo Título");
        verify(propertyRepository).save(existing);
    }

    @Test
    void updateProperty_setsPublishedAt_whenStatusChangesToActive() {
        Property existing = buildProperty();
        existing.setPublishedAt(null);
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenReturn(existing);

        UpdatePropertyRequest req = buildUpdateRequest(null, PropertyStatus.ACTIVE);

        propertyService.updateProperty(PROPERTY_ID, req);

        assertThat(existing.getPublishedAt()).isNotNull();
    }

    // ── deleteProperty ────────────────────────────────────────────────────────

    @Test
    void deleteProperty_setsDeletedAt() {
        Property existing = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(propertyRepository.save(any(Property.class))).thenReturn(existing);

        propertyService.deleteProperty(PROPERTY_ID);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(propertyRepository).save(existing);
    }

    // ── listProperties ────────────────────────────────────────────────────────

    @Test
    void listProperties_returnsMappedPage() {
        Page<Property> page = new PageImpl<>(List.of(buildProperty()));
        when(propertyRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        Page<PropertySummaryResponse> result = propertyService.listProperties(
                null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── tenantIsolation ───────────────────────────────────────────────────────

    @Test
    void tenantIsolation_alwaysPassesTenantIdFromContext() {
        UUID otherTenant = UUID.randomUUID();
        TenantContext.setTenantId(otherTenant.toString());

        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, otherTenant))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.getProperty(PROPERTY_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(propertyRepository).findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, otherTenant);
        verify(propertyRepository, never()).findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID);
    }

    // ── addPhoto ──────────────────────────────────────────────────────────────

    @Test
    void addPhoto_firstPhotoBecomesAutoCover() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(photoRepository.countByPropertyId(PROPERTY_ID)).thenReturn(0);
        when(fileStorageService.upload(any()))
                .thenReturn(new FileStorageService.UploadResult("1,01", "http://storage/1,01"));
        PropertyPhoto savedPhoto = buildPhoto(property, true);
        when(photoRepository.save(any())).thenReturn(savedPhoto);
        when(propertyRepository.save(any())).thenReturn(property);

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[100]);
        PropertyPhotoResponse response = propertyService.addPhoto(PROPERTY_ID, file, "alt text");

        assertThat(response.isCover()).isTrue();
        verify(propertyRepository).save(argThat(p -> PHOTO_ID.equals(p.getCoverPhotoId())));
    }

    @Test
    void addPhoto_secondPhotoNotCover() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(photoRepository.countByPropertyId(PROPERTY_ID)).thenReturn(1);
        when(fileStorageService.upload(any()))
                .thenReturn(new FileStorageService.UploadResult("1,02", "http://storage/1,02"));
        PropertyPhoto savedPhoto = buildPhoto(property, false);
        when(photoRepository.save(any())).thenReturn(savedPhoto);

        MockMultipartFile file = new MockMultipartFile("file", "photo2.jpg", "image/jpeg", new byte[100]);
        PropertyPhotoResponse response = propertyService.addPhoto(PROPERTY_ID, file, null);

        assertThat(response.isCover()).isFalse();
        verify(propertyRepository, never()).save(any());
    }

    // ── videos ───────────────────────────────────────────────────────────────

    @Test
    void addUploadedVideo_savesVideoWhenFileIsWithinLimit() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(videoRepository.countByPropertyId(PROPERTY_ID)).thenReturn(0);
        when(fileStorageService.upload(any()))
                .thenReturn(new FileStorageService.UploadResult("2,01", "http://storage/2,01"));
        when(videoRepository.save(any())).thenReturn(buildUploadedVideo(property));

        MockMultipartFile file = new MockMultipartFile("file", "tour.mp4", "video/mp4", new byte[100]);
        PropertyVideoResponse response = propertyService.addUploadedVideo(PROPERTY_ID, file, "Tour");

        assertThat(response.id()).isEqualTo(VIDEO_ID);
        assertThat(response.source()).isEqualTo(PropertyVideoSource.UPLOAD);
        assertThat(response.title()).isEqualTo("Tour");
        verify(videoRepository).save(argThat(video -> video.getPosition() == 0));
    }

    @Test
    void addUploadedVideo_rejectsFilesAbove25Mb() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(25L * 1024L * 1024L + 1L);

        assertThatThrownBy(() -> propertyService.addUploadedVideo(PROPERTY_ID, file, "Large"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("25MB");

        verify(fileStorageService, never()).upload(any());
    }

    @Test
    void addYoutubeVideo_normalizesUrlToEmbedUrl() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(videoRepository.countByPropertyId(PROPERTY_ID)).thenReturn(1);
        when(videoRepository.save(any())).thenAnswer(invocation -> {
            PropertyVideo video = invocation.getArgument(0);
            video.setId(VIDEO_ID);
            video.setCreatedAt(LocalDateTime.now());
            return video;
        });

        PropertyVideoResponse response = propertyService.addYoutubeVideo(
                PROPERTY_ID,
                new CreateYoutubeVideoRequest("https://youtu.be/dQw4w9WgXcQ", "Visita")
        );

        assertThat(response.source()).isEqualTo(PropertyVideoSource.YOUTUBE);
        assertThat(response.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.url()).isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        assertThat(response.position()).isEqualTo(1);
    }

    @Test
    void deleteVideo_deletesUploadedFileFromStorage() {
        Property property = buildProperty();
        PropertyVideo video = buildUploadedVideo(property);
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(videoRepository.findByIdAndPropertyId(VIDEO_ID, PROPERTY_ID))
                .thenReturn(Optional.of(video));
        when(videoRepository.findByPropertyIdOrderByPositionAsc(PROPERTY_ID))
                .thenReturn(List.of());

        propertyService.deleteVideo(PROPERTY_ID, VIDEO_ID);

        verify(fileStorageService).delete("2,01", "http://storage/2,01");
        verify(videoRepository).delete(video);
    }

    // ── setCoverPhoto ─────────────────────────────────────────────────────────

    @Test
    void setCoverPhoto_updatesIsCoverAndProperty() {
        Property property = buildProperty();
        PropertyPhoto photo = buildPhoto(property, false);
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(photoRepository.findByPropertyIdOrderByPositionAsc(PROPERTY_ID))
                .thenReturn(List.of(photo));

        propertyService.setCoverPhoto(PROPERTY_ID, PHOTO_ID);

        assertThat(photo.getIsCover()).isTrue();
        verify(propertyRepository).save(argThat(p -> PHOTO_ID.equals(p.getCoverPhotoId())));
    }

    // ── deletePhoto ───────────────────────────────────────────────────────────

    @Test
    void deletePhoto_deletesFromStorageAndRepo() {
        Property property = buildProperty();
        PropertyPhoto photo = buildPhoto(property, false);
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(photoRepository.findByIdAndPropertyId(PHOTO_ID, PROPERTY_ID))
                .thenReturn(Optional.of(photo));

        propertyService.deletePhoto(PROPERTY_ID, PHOTO_ID);

        verify(fileStorageService).delete("1,01", "http://storage/1,01");
        verify(photoRepository).delete(photo);
    }

    @Test
    void deletePhoto_whenCoverDeleted_promotesNextPhoto() {
        Property property = buildProperty();
        PropertyPhoto coverPhoto = buildPhoto(property, true);
        UUID nextPhotoId = UUID.randomUUID();
        PropertyPhoto nextPhoto = buildPhoto(property, false);
        nextPhoto.setId(nextPhotoId);

        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        when(photoRepository.findByIdAndPropertyId(PHOTO_ID, PROPERTY_ID))
                .thenReturn(Optional.of(coverPhoto));
        when(photoRepository.findByPropertyIdOrderByPositionAsc(PROPERTY_ID))
                .thenReturn(List.of(nextPhoto));

        propertyService.deletePhoto(PROPERTY_ID, PHOTO_ID);

        assertThat(nextPhoto.getIsCover()).isTrue();
        verify(propertyRepository).save(argThat(p -> nextPhotoId.equals(p.getCoverPhotoId())));
    }

    // ── addFloorPlan ──────────────────────────────────────────────────────────

    @Test
    void addFloorPlan_savesAndReturnsResponse() {
        Property property = buildProperty();
        when(propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));
        FloorPlan plan = buildFloorPlan(property);
        when(floorPlanRepository.save(any())).thenReturn(plan);

        CreateFloorPlanRequest req = new CreateFloorPlanRequest("Térreo", new BigDecimal("80"), null, null, null, null, null, null);
        FloorPlanResponse response = propertyService.addFloorPlan(PROPERTY_ID, req);

        assertThat(response.id()).isEqualTo(FLOOR_PLAN_ID);
        assertThat(response.name()).isEqualTo("Térreo");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Property buildProperty() {
        Property p = new Property();
        p.setId(PROPERTY_ID);
        p.setTenantId(TENANT_ID);
        p.setTitle("Casa Teste");
        p.setOperation(PropertyOperation.SALE);
        p.setPropertyType(PropertyType.HOUSE);
        p.setStatus(PropertyStatus.DRAFT);
        p.setCurrency("BRL");
        p.setAddressCountry("BR");
        p.setAttributes(new HashMap<>());
        p.setCategories(new HashSet<>());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private PropertyPhoto buildPhoto(Property property, boolean isCover) {
        PropertyPhoto photo = new PropertyPhoto();
        photo.setId(PHOTO_ID);
        photo.setProperty(property);
        photo.setSeaweedFid("1,01");
        photo.setUrl("http://storage/1,01");
        photo.setPosition(0);
        photo.setIsCover(isCover);
        photo.setCreatedAt(LocalDateTime.now());
        return photo;
    }

    private FloorPlan buildFloorPlan(Property property) {
        FloorPlan plan = new FloorPlan();
        plan.setId(FLOOR_PLAN_ID);
        plan.setProperty(property);
        plan.setName("Térreo");
        plan.setArea(new BigDecimal("80"));
        plan.setCreatedAt(LocalDateTime.now());
        plan.setPhotos(new ArrayList<>());
        return plan;
    }

    private PropertyVideo buildUploadedVideo(Property property) {
        PropertyVideo video = new PropertyVideo();
        video.setId(VIDEO_ID);
        video.setProperty(property);
        video.setTenantId(TENANT_ID);
        video.setSource(PropertyVideoSource.UPLOAD);
        video.setSeaweedFid("2,01");
        video.setUrl("http://storage/2,01");
        video.setTitle("Tour");
        video.setPosition(0);
        video.setCreatedAt(LocalDateTime.now());
        return video;
    }

    private UpdatePropertyRequest buildUpdateRequest(String title, PropertyStatus status) {
        return new UpdatePropertyRequest(
                title,           // title
                null,            // slug
                null,            // description
                null,            // operation
                null,            // propertyType
                status,          // status
                null,            // condition
                null,            // referenceCode
                null,            // featured
                null,            // price
                null,            // currency
                null,            // taxes
                null,            // condoFee
                null,            // areaTotal
                null,            // areaUseful
                null,            // bedrooms
                null,            // suites
                null,            // bathrooms
                null,            // parking
                null,            // floorNumber
                null,            // addressStreet
                null,            // addressNumber
                null,            // addressComplement
                null,            // addressNeighborhood
                null,            // addressCity
                null,            // addressState
                null,            // addressCountry
                null,            // addressZip
                null,            // lat
                null,            // lng
                null,            // attributes
                null,            // categoryIds
                null,            // availableToAllBrokers
                null             // brokerIds
        );
    }
}
