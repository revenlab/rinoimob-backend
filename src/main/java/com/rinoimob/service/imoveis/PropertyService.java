package com.rinoimob.service.imoveis;

import com.rinoimob.domain.dto.property.*;
import com.rinoimob.domain.entity.FloorPlan;
import com.rinoimob.domain.entity.FloorPlanPhoto;
import com.rinoimob.domain.entity.Property;
import com.rinoimob.domain.entity.PropertyCategory;
import com.rinoimob.domain.entity.PropertyPhoto;
import com.rinoimob.domain.entity.PropertyVideo;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import com.rinoimob.domain.enums.PropertyVideoSource;
import com.rinoimob.domain.repository.FloorPlanPhotoRepository;
import com.rinoimob.domain.repository.PropertyCategoryRepository;
import com.rinoimob.domain.repository.PropertySpecification;
import com.rinoimob.domain.repository.FloorPlanRepository;
import com.rinoimob.domain.repository.PropertyPhotoRepository;
import com.rinoimob.domain.repository.PropertyRepository;
import com.rinoimob.domain.repository.PropertyVideoRepository;
import com.rinoimob.service.billing.TenantQuotaEnforcementService;
import com.rinoimob.service.storage.FileStorageService;
import com.rinoimob.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository photoRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanPhotoRepository floorPlanPhotoRepository;
    private final PropertyVideoRepository videoRepository;
    private final PropertyCategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final CategoryService categoryService;
    private final PropertyTypeService propertyTypeService;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private static final long MAX_VIDEO_UPLOAD_BYTES = 25L * 1024L * 1024L;
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})"
    );

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public PropertyResponse createProperty(CreatePropertyRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        tenantQuotaEnforcementService.assertCanCreateProperty(tenantId);
        propertyTypeService.assertActive(tenantId, req.propertyType());
        Property property = new Property();
        property.setTenantId(tenantId);
        applyRequest(property, req);
        property.setReferenceCode(resolveReferenceCode(tenantId, req.referenceCode()));
        property.setStatus(req.status() != null ? req.status() : PropertyStatus.DRAFT);
        property = propertyRepository.save(property);
        log.info("Property created id={} tenant={}", property.getId(), tenantId);
        return toResponse(property);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public PropertyResponse updateProperty(UUID id, UpdatePropertyRequest req) {
        Property property = findOwnedProperty(id);
        if (req.propertyType() != null && req.propertyType() != property.getPropertyType()) {
            propertyTypeService.assertActive(property.getTenantId(), req.propertyType());
        }
        applyUpdate(property, req);
        property = propertyRepository.save(property);
        log.info("Property updated id={}", id);
        return toResponse(property);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public void deleteProperty(UUID id) {
        Property property = findOwnedProperty(id);
        property.setDeletedAt(LocalDateTime.now());
        propertyRepository.save(property);
        log.info("Property soft-deleted id={}", id);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "publicPropertyDetails",
            key = "T(com.rinoimob.context.TenantContext).getTenantId() + ':' + #id"
    )
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
        return listProperties(status, operation, propertyType, minPrice, maxPrice, bedrooms, city, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PropertySummaryResponse> listProperties(
            PropertyStatus status,
            PropertyOperation operation,
            PropertyType propertyType,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            String city,
            String queryText,
            Pageable pageable) {
        return listProperties(status, operation, propertyType, categorySlug, minPrice, maxPrice, bedrooms, city, queryText, null, null, null, null, pageable);
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
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusKm,
            Pageable pageable) {
        return listProperties(status, operation, propertyType, null, minPrice, maxPrice, bedrooms, city, null, latitude, longitude, radiusKm, null, pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "publicPropertyListings",
            key = "T(com.rinoimob.context.TenantContext).getTenantId() + ':' + #status + ':' + #operation + ':' + #propertyType + ':' + #categorySlug + ':' + #minPrice + ':' + #maxPrice + ':' + #bedrooms + ':' + #city + ':' + #queryText + ':' + #latitude + ':' + #longitude + ':' + #radiusKm + ':' + #featured + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()"
    )
    public Page<PropertySummaryResponse> listProperties(
            PropertyStatus status,
            PropertyOperation operation,
            PropertyType propertyType,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            String city,
            String queryText,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal radiusKm,
            Boolean featured,
            Pageable pageable) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyRepository.findAll(
                PropertySpecification.withFilters(
                        tenantId, status, operation, propertyType, categorySlug, minPrice, maxPrice, bedrooms, city, queryText,
                        latitude, longitude, radiusKm, featured),
                pageable)
                .map(this::toSummary);
    }

    // ── PHOTOS ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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

    // ── VIDEOS ───────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public PropertyVideoResponse addUploadedVideo(UUID propertyId, MultipartFile file, String title) {
        Property property = findOwnedProperty(propertyId);
        validateVideoUpload(file);

        FileStorageService.UploadResult result = fileStorageService.upload(file);
        PropertyVideo video = new PropertyVideo();
        video.setProperty(property);
        video.setTenantId(property.getTenantId());
        video.setSource(PropertyVideoSource.UPLOAD);
        video.setSeaweedFid(result.fid());
        video.setUrl(result.url());
        video.setTitle(normalizeVideoTitle(title));
        video.setPosition(videoRepository.countByPropertyId(propertyId));
        video = videoRepository.save(video);

        log.info("Video uploaded to property={} fid={}", propertyId, result.fid());
        return toVideoResponse(video);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public PropertyVideoResponse addYoutubeVideo(UUID propertyId, CreateYoutubeVideoRequest request) {
        Property property = findOwnedProperty(propertyId);
        String videoId = extractYoutubeVideoId(request.url());
        String embedUrl = "https://www.youtube.com/embed/" + videoId;

        PropertyVideo video = new PropertyVideo();
        video.setProperty(property);
        video.setTenantId(property.getTenantId());
        video.setSource(PropertyVideoSource.YOUTUBE);
        video.setUrl(embedUrl);
        video.setYoutubeVideoId(videoId);
        video.setTitle(normalizeVideoTitle(request.title()));
        video.setPosition(videoRepository.countByPropertyId(propertyId));
        video = videoRepository.save(video);

        log.info("YouTube video added to property={} videoId={}", propertyId, videoId);
        return toVideoResponse(video);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public void deleteVideo(UUID propertyId, UUID videoId) {
        findOwnedProperty(propertyId);
        PropertyVideo video = videoRepository.findByIdAndPropertyId(videoId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        if (video.getSource() == PropertyVideoSource.UPLOAD && video.getSeaweedFid() != null) {
            fileStorageService.delete(video.getSeaweedFid(), video.getUrl());
        }

        videoRepository.delete(video);
        List<PropertyVideo> remaining = videoRepository.findByPropertyIdOrderByPositionAsc(propertyId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        videoRepository.saveAll(remaining);
        log.info("Video deleted id={} from property={}", videoId, propertyId);
    }

    // ── FLOOR PLANS ──────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
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

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public void setFloorPlanPhotoCover(UUID propertyId, UUID planId, UUID photoId) {
        findOwnedProperty(propertyId);
        FloorPlan plan = floorPlanRepository.findByIdAndPropertyId(planId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));
        List<FloorPlanPhoto> photos = floorPlanPhotoRepository.findByFloorPlanIdOrderByPositionAsc(plan.getId());
        FloorPlanPhoto selected = photos.stream()
                .filter(photo -> photo.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan photo not found"));

        photos.remove(selected);
        photos.add(0, selected);
        renumberFloorPlanPhotos(photos);
        floorPlanPhotoRepository.saveAll(photos);
        log.info("Floor plan cover photo set to={} on plan={} property={}", photoId, planId, propertyId);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicPropertyListings", "publicPropertyDetails"}, allEntries = true)
    public void deleteFloorPlanPhoto(UUID propertyId, UUID planId, UUID photoId) {
        findOwnedProperty(propertyId);
        FloorPlan plan = floorPlanRepository.findByIdAndPropertyId(planId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan not found"));
        FloorPlanPhoto photo = floorPlanPhotoRepository.findByIdAndFloorPlanId(photoId, plan.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor plan photo not found"));

        fileStorageService.delete(photo.getSeaweedFid(), photo.getUrl());
        floorPlanPhotoRepository.delete(photo);
        List<FloorPlanPhoto> remaining = floorPlanPhotoRepository.findByFloorPlanIdOrderByPositionAsc(plan.getId());
        renumberFloorPlanPhotos(remaining);
        floorPlanPhotoRepository.saveAll(remaining);
        log.info("Floor plan photo deleted id={} from plan={} property={}", photoId, planId, propertyId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Property findOwnedProperty(UUID id) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return propertyRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
    }

    private void applyRequest(Property p, CreatePropertyRequest req) {
        // Generate slug if not provided
        if (req.slug() == null || req.slug().isBlank()) {
            p.setSlug(generateSlug(req.title(), req.propertyType(), req.addressCity(), p.getTenantId()));
        } else {
            p.setSlug(req.slug());
        }

        // Generate title if empty (user can override)
        if (req.title() == null || req.title().isBlank()) {
            p.setPropertyType(req.propertyType());
            p.setBedrooms(req.bedrooms());
            p.setAddressCity(req.addressCity());
            p.setOperation(req.operation());
            p.setTitle(generateDynamicTitle(p));
        } else {
            p.setTitle(req.title().trim());
        }

        // Generate description if empty (user can override)
        if (req.description() == null || req.description().isBlank()) {
            p.setAreaTotal(req.areaTotal());
            p.setSuites(req.suites());
            p.setBathrooms(req.bathrooms());
            p.setParking(req.parking());
            p.setCondition(req.condition());
            p.setAttributes(req.attributes() != null ? req.attributes() : new java.util.HashMap<>());
            p.setDescription(generateDynamicDescription(p));
        } else {
            p.setDescription(req.description());
        }

        p.setOperation(req.operation());
        p.setPropertyType(req.propertyType());
        p.setCondition(req.condition());
        p.setReferenceCode(normalizeReferenceCode(req.referenceCode()));
        p.setFeatured(Boolean.TRUE.equals(req.featured()));
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
        p.setFloorNumber(req.floorNumber());
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
        if (req.categoryIds() != null) p.setCategories(resolveCategories(req.categoryIds()));
    }

    private void applyUpdate(Property p, UpdatePropertyRequest req) {
        // Update slug if provided or regenerate if emptied
        if (req.slug() != null && !req.slug().isBlank()) {
            p.setSlug(req.slug());
        } else if (req.title() != null && !req.title().isBlank()) {
            // If title is updated but slug is empty, regenerate slug from new title
            p.setSlug(generateSlug(req.title(), req.propertyType(), req.addressCity(), p.getTenantId()));
        }
        if (req.title() != null && !req.title().isBlank()) {
            p.setTitle(req.title());
        }
        if (req.description() != null) {
            p.setDescription(req.description());
        }
        if (req.operation() != null) p.setOperation(req.operation());
        if (req.propertyType() != null) p.setPropertyType(req.propertyType());
        if (req.condition() != null) p.setCondition(req.condition());
        if (req.referenceCode() != null) p.setReferenceCode(req.referenceCode());
        if (req.featured() != null) p.setFeatured(req.featured());
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
        if (req.floorNumber() != null) p.setFloorNumber(req.floorNumber());
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
        if (req.categoryIds() != null) p.setCategories(resolveCategories(req.categoryIds()));
    }

    private Set<PropertyCategory> resolveCategories(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        Set<PropertyCategory> resolved = new HashSet<>();
        for (UUID catId : ids) {
            categoryRepository.findById(catId).ifPresent(cat -> {
                // Allow global or own-tenant categories only
                if (cat.isGlobal() || tenantId.equals(cat.getTenantId())) {
                    resolved.add(cat);
                }
            });
        }
        return resolved;
    }

    private String resolveReferenceCode(UUID tenantId, String requestedCode) {
        String normalizedCode = normalizeReferenceCode(requestedCode);
        if (normalizedCode != null) {
            if (propertyRepository.existsByTenantIdAndReferenceCodeAndDeletedAtIsNull(tenantId, normalizedCode)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reference code already exists");
            }
            return normalizedCode;
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String generatedCode = "IMV-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 8).toUpperCase();
            if (!propertyRepository.existsByTenantIdAndReferenceCodeAndDeletedAtIsNull(tenantId, generatedCode)) {
                return generatedCode;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to generate a unique reference code");
    }

    private String normalizeReferenceCode(String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return null;
        }
        return referenceCode.trim().toUpperCase();
    }

    private PropertyResponse toResponse(Property p) {
        List<com.rinoimob.domain.dto.CategoryResponse> cats = p.getCategories().stream()
                .map(categoryService::toResponse).toList();
        return new PropertyResponse(
                p.getId(), p.getTitle(), p.getSlug(), p.getDescription(),
                p.getOperation(), p.getPropertyType(), p.getStatus(),
                p.getCondition(), p.getReferenceCode(), p.isFeatured(),
                p.getPrice(), p.getCurrency(), p.getTaxes(), p.getCondoFee(),
                p.getAreaTotal(), p.getAreaUseful(),
                p.getBedrooms(), p.getSuites(), p.getBathrooms(), p.getParking(),
                p.getFloorNumber(),
                p.getAddressStreet(), p.getAddressNumber(), p.getAddressComplement(),
                p.getAddressNeighborhood(), p.getAddressCity(), p.getAddressState(),
                p.getAddressCountry(), p.getAddressZip(),
                p.getLat(), p.getLng(), p.getCoverPhotoId(),
                p.getAttributes(), cats,
                p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getPhotos().stream().map(this::toPhotoResponse).toList(),
                p.getFloorPlans().stream().map(this::toFloorPlanResponse).toList(),
                p.getVideos().stream().map(this::toVideoResponse).toList()
        );
    }

    private PropertySummaryResponse toSummary(Property p) {
        String coverUrl = p.getPhotos().stream()
                .filter(ph -> Boolean.TRUE.equals(ph.getIsCover()))
                .findFirst()
                .map(PropertyPhoto::getUrl)
                .orElse(null);
        List<com.rinoimob.domain.dto.CategoryResponse> cats = p.getCategories().stream()
                .map(categoryService::toResponse).toList();
        return new PropertySummaryResponse(
                p.getId(), p.getTitle(), p.getOperation(), p.getPropertyType(), p.getStatus(),
                p.getCondition(), p.getReferenceCode(), p.isFeatured(),
                p.getPrice(), p.getCurrency(), p.getAreaTotal(), p.getBedrooms(), p.getBathrooms(),
                p.getAddressCity(), p.getAddressState(), p.getAddressCountry(),
                p.getCoverPhotoId(), coverUrl, cats, p.getCreatedAt()
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
        return new FloorPlanPhotoResponse(fpp.getId(), fpp.getUrl(), fpp.getPosition(),
                fpp.getPosition() != null && fpp.getPosition() == 0, fpp.getCreatedAt());
    }

    private PropertyVideoResponse toVideoResponse(PropertyVideo video) {
        return new PropertyVideoResponse(video.getId(), video.getSource(), video.getUrl(),
                video.getYoutubeVideoId(), video.getTitle(), video.getPosition(), video.getCreatedAt());
    }

    private void validateVideoUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is required");
        }
        if (file.getSize() > MAX_VIDEO_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file must be at most 25MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("video/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be a video");
        }
    }

    private String extractYoutubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "YouTube URL is required");
        }

        Matcher matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(url.trim());
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid YouTube URL");
        }

        return matcher.group(1);
    }

    private String normalizeVideoTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private void renumberFloorPlanPhotos(List<FloorPlanPhoto> photos) {
        for (int i = 0; i < photos.size(); i++) {
            photos.get(i).setPosition(i);
        }
    }

    private String generateSlug(String title, PropertyType propertyType, String city, UUID tenantId) {
        String base = slugify(title);

        if (base.isBlank()) {
            String typeLabel = propertyType != null ? propertyType.name().toLowerCase() : "property";
            String cityLabel = city != null ? slugify(city) : "default";
            base = typeLabel + "-" + cityLabel;
        }

        String candidate = base;
        int counter = 1;

        while (propertyRepository.existsByTenantIdAndSlugAndDeletedAtIsNull(tenantId, candidate)) {
            candidate = base + "-" + counter++;
        }

        return candidate;
    }

    private String generateDynamicTitle(Property p) {
        StringBuilder sb = new StringBuilder();

        if (p.getBedrooms() != null && p.getBedrooms() > 0) {
            sb.append(p.getBedrooms()).append("q");
        }

        if (sb.length() > 0) sb.append(" - ");

        if (p.getPropertyType() != null) {
            String typeName = p.getPropertyType().name().toLowerCase();
            typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
            sb.append(typeName);
        }

        if (p.getAddressCity() != null && !p.getAddressCity().isBlank()) {
            sb.append(" em ").append(p.getAddressCity());
        }

        if (p.getOperation() != null) {
            String operationLabel = p.getOperation().name().toLowerCase();
            if (operationLabel.equals("sale")) operationLabel = "Venda";
            else if (operationLabel.equals("rent")) operationLabel = "Aluguel";
            else if (operationLabel.equals("seasonal")) operationLabel = "Temporada";
            else operationLabel = operationLabel.substring(0, 1).toUpperCase() + operationLabel.substring(1);

            sb.append(" - ").append(operationLabel);
        }

        return sb.toString().trim();
    }

    private String generateDynamicDescription(Property p) {
        StringBuilder sb = new StringBuilder();

        sb.append("Imóvel");

        if (p.getAreaTotal() != null) {
            sb.append(" com ").append(p.getAreaTotal()).append(" m²");
        }

        if (p.getBedrooms() != null && p.getBedrooms() > 0) {
            sb.append(", ").append(p.getBedrooms()).append(" quarto(s)");
        }

        if (p.getSuites() != null && p.getSuites() > 0) {
            sb.append(", ").append(p.getSuites()).append(" suíte(s)");
        }

        if (p.getBathrooms() != null && p.getBathrooms() > 0) {
            sb.append(", ").append(p.getBathrooms()).append(" banheiro(s)");
        }

        if (p.getParking() != null && p.getParking() > 0) {
            sb.append(", ").append(p.getParking()).append(" vaga(s) de garagem");
        }

        sb.append(".");

        if (p.getCondition() != null) {
            String conditionLabel = p.getCondition().name().toLowerCase();
            if (conditionLabel.equals("new")) conditionLabel = "Novo";
            else if (conditionLabel.equals("used")) conditionLabel = "Usado";
            else if (conditionLabel.equals("under_construction")) conditionLabel = "Em construção";

            sb.append(" Imóvel em ").append(conditionLabel).append(".");
        }

        if (p.getAttributes() != null && !p.getAttributes().isEmpty()) {
            List<String> amenities = new ArrayList<>();
            for (Map.Entry<String, Object> entry : p.getAttributes().entrySet()) {
                if (entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                    String amenityName = entry.getKey()
                        .replaceAll("([a-z])([A-Z])", "$1 $2")
                        .toLowerCase();
                    amenities.add(amenityName);
                }
            }

            if (!amenities.isEmpty()) {
                sb.append(" Acesso a: ").append(String.join(", ", amenities)).append(".");
            }
        }

        return sb.toString();
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) return "";

        return value
            .toLowerCase()
            .replaceAll("[àáäâ]", "a")
            .replaceAll("[èéëê]", "e")
            .replaceAll("[ìíïî]", "i")
            .replaceAll("[òóöô]", "o")
            .replaceAll("[ùúüû]", "u")
            .replaceAll("[ç]", "c")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
