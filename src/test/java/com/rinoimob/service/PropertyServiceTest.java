package com.rinoimob.service;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.entity.FloorPlan;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.storage.FileStorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
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

    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyPhotoRepository photoRepository;
    @Mock private FloorPlanRepository floorPlanRepository;
    @Mock private FloorPlanPhotoRepository floorPlanPhotoRepository;
    @Mock private FileStorageService fileStorageService;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID.toString());
        propertyService = new PropertyService(
                propertyRepository, photoRepository, floorPlanRepository,
                floorPlanPhotoRepository, fileStorageService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── createProperty ────────────────────────────────────────────────────────

    @Test
    void createProperty_savesAndReturnsMappedResponse() {
        CreatePropertyRequest req = new CreatePropertyRequest(
                "Casa Teste", "Descrição", PropertyOperation.SALE, PropertyType.HOUSE,
                PropertyStatus.DRAFT, new BigDecimal("500000"), "BRL", null, null,
                new BigDecimal("120"), new BigDecimal("100"), 3, 1, 2, 2,
                "Rua A", "10", null, "Bairro", "São Paulo", "SP", "BR", "01001-000",
                null, null, null);

        Property savedProperty = buildProperty();
        when(propertyRepository.save(any(Property.class))).thenReturn(savedProperty);

        PropertyResponse response = propertyService.createProperty(req);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(PROPERTY_ID);
        assertThat(response.title()).isEqualTo("Casa Teste");
        verify(propertyRepository).save(any(Property.class));
    }

    @Test
    void createProperty_defaultsToDraftStatus_whenStatusNull() {
        CreatePropertyRequest req = new CreatePropertyRequest(
                "Casa", null, PropertyOperation.RENT, PropertyType.APARTMENT,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

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

        UpdatePropertyRequest req = new UpdatePropertyRequest(
                "Novo Título", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

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

        UpdatePropertyRequest req = new UpdatePropertyRequest(
                null, null, null, null, PropertyStatus.ACTIVE, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

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
        when(propertyRepository.findWithFilters(
                eq(TENANT_ID), any(), any(), any(), any(), any(), any(), any(), any()))
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

        CreateFloorPlanRequest req = new CreateFloorPlanRequest("Térreo", new BigDecimal("80"));
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
}
