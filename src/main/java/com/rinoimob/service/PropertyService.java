package com.rinoimob.service;

import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.entity.FloorPlan;
import com.rinoimob.domain.entity.FloorPlanPhoto;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.repository.FloorPlanPhotoRepository;
import com.rinoimob.domain.repository.PropertySpecification;
import com.rinoimob.domain.repository.FloorPlanRepository;
import com.rinoimob.domain.repository.PropertyPhotoRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.service.storage.FileStorageService;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository photoRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanPhotoRepository floorPlanPhotoRepository;
    private final FileStorageService fileStorageService;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        Property property = new Property();
        property.setTenantId(tenantId);
        applyRequest(property, req);
        property.setStatus(req.status() != null ? req.status() : PropertyStatus.DRAFT);
        property = propertyRepository.save(property);
        log.info("Property created id={} tenant={}", property.getId(), tenantId);
        return toResponse(property);
    }

    @Transactional
    public PropertyResponse updateProperty(UUID id, UpdatePropertyRequest req) {
        Property property = findOwnedProperty(id);
        applyUpdate(property, req);
        property = propertyRepository.save(property);
        log.info("Property updated id={}", id);
        return toResponse(property);
    }

    @Transactional
    public void deleteProperty(UUID id) {
        Property property = findOwnedProperty(id);
        property.setDeletedAt(LocalDateTime.now());
        propertyRepository.save(property);
        log.info("Property soft-deleted id={}", id);
    }

    @Transactional(readOnly = true)
    public PropertyResponse getProperty(UUID id) {
        return toResponse(findOwnedProperty(id));
    }

    @Transactional(readOnly = true)
    public Page<PropertySummaryResponse> listProperties(
            PropertyStatus status,
            PropertyOperation operation,
            PropertyType propertyType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            String city,
            Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyRepository.findAll(
                PropertySpecification.withFilters(
                        tenantId, status, operation, propertyType, minPrice, maxPrice, bedrooms, city),
                pageable)
                .map(this::toSummary);
    }

    // ── PHOTOS ────────────────────────────────────────────────────────────────

    @Transactional
    public PropertyPhotoResponse addPhoto(UUID propertyId, MultipartFile file, String altText) {
        Property property = findOwnedProperty(propertyId);
        FileStorageService.UploadResult result = fileStorageService.upload(file);

        int nextPosition = photoRepository.countByPropertyId(propertyId);
        boolean isFirstPhoto = nextPosition == 0;

        PropertyPhoto photo = new PropertyPhoto();
        photo.setProperty(property);
        photo.setSeaweedFid(result.fid());
        photo.setUrl(result.url());
        photo.setPosition(nextPosition);
        photo.setIsCover(isFirstPhoto);
        photo.setAltText(altText);
        photo = photoRepository.save(photo);

        // First photo automatically becomes the cover
        if (isFirstPhoto) {
            property.setCoverPhotoId(photo.getId());
            propertyRepository.save(property);
        }

        log.info("Photo added to property={} fid={}", propertyId, result.fid());
        return toPhotoResponse(photo);
    }

    @Transactional
    public void setCoverPhoto(UUID propertyId, UUID photoId) {
        Property property = findOwnedProperty(propertyId);

        // Clear current cover
        List<PropertyPhoto> photos = photoRepository.findByPropertyIdOrderByPositionAsc(propertyId);
        photos.forEach(p -> p.setIsCover(false));
        photoRepository.saveAll(photos);

        // Set new cover
        PropertyPhoto coverPhoto = photos.stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        coverPhoto.setIsCover(true);
        photoRepository.save(coverPhoto);

        property.setCoverPhotoId(photoId);
        propertyRepository.save(property);
        log.info("Cover photo set to={} on property={}", photoId, propertyId);
    }

    @Transactional
    public void deletePhoto(UUID propertyId, UUID photoId) {
        Property property = findOwnedProperty(propertyId);
        PropertyPhoto photo = photoRepository.findByIdAndPropertyId(photoId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));

        boolean wasCover = Boolean.TRUE.equals(photo.getIsCover());
        fileStorageService.delete(photo.getSeaweedFid(), photo.getUrl());
        photoRepository.delete(photo);

        if (wasCover) {
            // Assign cover to the first remaining photo
            photoRepository.findByPropertyIdOrderByPositionAsc(propertyId).stream()
                    .findFirst()
                    .ifPresentOrElse(next -> {
                        next.setIsCover(true);
                        photoRepository.save(next);
                        property.setCoverPhotoId(next.getId());
                    }, () -> property.setCoverPhotoId(null));
            propertyRepository.save(property);
        }
        log.info("Photo deleted id={} from property={}", photoId, propertyId);
    }

    // ── FLOOR PLANS ──────────────────────────────────────────────────────────

    @Transactional
    public FloorPlanResponse addFloorPlan(UUID propertyId, CreateFloorPlanRequest req) {
        Property property = findOwnedProperty(propertyId);
        FloorPlan plan = new FloorPlan();
        plan.setProperty(property);
        plan.setName(req.name());
        plan.setArea(req.area());
        plan = floorPlanRepository.save(plan);
        log.info("Floor plan added to property={} name={}", propertyId, req.name());
        return toFloorPlanResponse(plan);
    }

    @Transactional
    public void deleteFloorPlan(UUID propertyId, UUID planId) {
        findOwnedProperty(propertyId);
        FloorPlan plan = floorPlanRepository.findByIdAndPropertyId(planId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));
        // Delete all photos from SeaweedFS before removing the plan
        plan.getPhotos().forEach(p -> fileStorageService.delete(p.getSeaweedFid(), p.getUrl()));
        floorPlanRepository.delete(plan);
        log.info("Floor plan deleted id={} from property={}", planId, propertyId);
    }

    @Transactional
    public FloorPlanPhotoResponse addFloorPlanPhoto(UUID propertyId, UUID planId, MultipartFile file) {
        findOwnedProperty(propertyId);
        FloorPlan plan = floorPlanRepository.findByIdAndPropertyId(planId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));

        FileStorageService.UploadResult result = fileStorageService.upload(file);
        int nextPosition = floorPlanPhotoRepository.countByFloorPlanId(planId);

        FloorPlanPhoto photo = new FloorPlanPhoto();
        photo.setFloorPlan(plan);
        photo.setSeaweedFid(result.fid());
        photo.setUrl(result.url());
        photo.setPosition(nextPosition);
        photo = floorPlanPhotoRepository.save(photo);
        return toFloorPlanPhotoResponse(photo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Property findOwnedProperty(UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
    }

    private void applyRequest(Property p, CreatePropertyRequest req) {
        p.setTitle(req.title());
        p.setDescription(req.description());
        p.setOperation(req.operation());
        p.setPropertyType(req.propertyType());
        p.setPrice(req.price());
        p.setCurrency(req.currency() != null ? req.currency() : "BRL");
        p.setTaxes(req.taxes());
        p.setCondoFee(req.condoFee());
        p.setAreaTotal(req.areaTotal());
        p.setAreaUseful(req.areaUseful());
        p.setBedrooms(req.bedrooms());
        p.setSuites(req.suites());
        p.setBathrooms(req.bathrooms());
        p.setParking(req.parking());
        p.setAddressStreet(req.addressStreet());
        p.setAddressNumber(req.addressNumber());
        p.setAddressComplement(req.addressComplement());
        p.setAddressNeighborhood(req.addressNeighborhood());
        p.setAddressCity(req.addressCity());
        p.setAddressState(req.addressState());
        p.setAddressCountry(req.addressCountry() != null ? req.addressCountry() : "BR");
        p.setAddressZip(req.addressZip());
        p.setLat(req.lat());
        p.setLng(req.lng());
        if (req.attributes() != null) p.setAttributes(req.attributes());
    }

    private void applyUpdate(Property p, UpdatePropertyRequest req) {
        if (req.title() != null) p.setTitle(req.title());
        if (req.description() != null) p.setDescription(req.description());
        if (req.operation() != null) p.setOperation(req.operation());
        if (req.propertyType() != null) p.setPropertyType(req.propertyType());
        if (req.status() != null) {
            p.setStatus(req.status());
            if (req.status() == PropertyStatus.ACTIVE && p.getPublishedAt() == null) {
                p.setPublishedAt(LocalDateTime.now());
            }
        }
        if (req.price() != null) p.setPrice(req.price());
        if (req.currency() != null) p.setCurrency(req.currency());
        if (req.taxes() != null) p.setTaxes(req.taxes());
        if (req.condoFee() != null) p.setCondoFee(req.condoFee());
        if (req.areaTotal() != null) p.setAreaTotal(req.areaTotal());
        if (req.areaUseful() != null) p.setAreaUseful(req.areaUseful());
        if (req.bedrooms() != null) p.setBedrooms(req.bedrooms());
        if (req.suites() != null) p.setSuites(req.suites());
        if (req.bathrooms() != null) p.setBathrooms(req.bathrooms());
        if (req.parking() != null) p.setParking(req.parking());
        if (req.addressStreet() != null) p.setAddressStreet(req.addressStreet());
        if (req.addressNumber() != null) p.setAddressNumber(req.addressNumber());
        if (req.addressComplement() != null) p.setAddressComplement(req.addressComplement());
        if (req.addressNeighborhood() != null) p.setAddressNeighborhood(req.addressNeighborhood());
        if (req.addressCity() != null) p.setAddressCity(req.addressCity());
        if (req.addressState() != null) p.setAddressState(req.addressState());
        if (req.addressCountry() != null) p.setAddressCountry(req.addressCountry());
        if (req.addressZip() != null) p.setAddressZip(req.addressZip());
        if (req.lat() != null) p.setLat(req.lat());
        if (req.lng() != null) p.setLng(req.lng());
        if (req.attributes() != null) p.setAttributes(req.attributes());
    }

    private PropertyResponse toResponse(Property p) {
        return new PropertyResponse(
                p.getId(), p.getTitle(), p.getDescription(),
                p.getOperation(), p.getPropertyType(), p.getStatus(),
                p.getPrice(), p.getCurrency(), p.getTaxes(), p.getCondoFee(),
                p.getAreaTotal(), p.getAreaUseful(),
                p.getBedrooms(), p.getSuites(), p.getBathrooms(), p.getParking(),
                p.getAddressStreet(), p.getAddressNumber(), p.getAddressComplement(),
                p.getAddressNeighborhood(), p.getAddressCity(), p.getAddressState(),
                p.getAddressCountry(), p.getAddressZip(),
                p.getLat(), p.getLng(), p.getCoverPhotoId(),
                p.getAttributes(), p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getPhotos().stream().map(this::toPhotoResponse).toList(),
                p.getFloorPlans().stream().map(this::toFloorPlanResponse).toList()
        );
    }

    private PropertySummaryResponse toSummary(Property p) {
        String coverUrl = p.getPhotos().stream()
                .filter(ph -> Boolean.TRUE.equals(ph.getIsCover()))
                .findFirst()
                .map(PropertyPhoto::getUrl)
                .orElse(null);
        return new PropertySummaryResponse(
                p.getId(), p.getTitle(), p.getOperation(), p.getPropertyType(), p.getStatus(),
                p.getPrice(), p.getCurrency(), p.getAreaTotal(), p.getBedrooms(), p.getBathrooms(),
                p.getAddressCity(), p.getAddressState(), p.getAddressCountry(),
                p.getCoverPhotoId(), coverUrl, p.getCreatedAt()
        );
    }

    private PropertyPhotoResponse toPhotoResponse(PropertyPhoto ph) {
        return new PropertyPhotoResponse(ph.getId(), ph.getUrl(), ph.getPosition(),
                ph.getIsCover(), ph.getAltText(), ph.getCreatedAt());
    }

    private FloorPlanResponse toFloorPlanResponse(FloorPlan fp) {
        return new FloorPlanResponse(fp.getId(), fp.getName(), fp.getArea(), fp.getCreatedAt(),
                fp.getPhotos().stream().map(this::toFloorPlanPhotoResponse).toList());
    }

    private FloorPlanPhotoResponse toFloorPlanPhotoResponse(FloorPlanPhoto fpp) {
        return new FloorPlanPhotoResponse(fpp.getId(), fpp.getUrl(), fpp.getPosition(), fpp.getCreatedAt());
    }
}
