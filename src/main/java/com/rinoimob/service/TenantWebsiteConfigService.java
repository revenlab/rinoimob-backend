package com.rinoimob.service;

import com.rinoimob.domain.dto.TenantWebsiteConfigResponse;
import com.rinoimob.domain.dto.UpdateTenantWebsiteConfigRequest;
import com.rinoimob.domain.entity.TenantWebsiteConfig;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.TenantWebsiteConfigRepository;
import com.rinoimob.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantWebsiteConfigService {

    private final TenantWebsiteConfigRepository tenantWebsiteConfigRepository;
    private final TenantRepository tenantRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public TenantWebsiteConfigResponse getConfig(UUID tenantId) {
        return toResponse(getOrCreateConfig(tenantId));
    }

    @Transactional
    public TenantWebsiteConfigResponse updateConfig(UUID tenantId, UpdateTenantWebsiteConfigRequest request) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);

        if (request.companyName() != null) config.setCompanyName(request.companyName());
        if (request.logoUrl() != null) config.setLogoUrl(request.logoUrl());
        if (request.faviconUrl() != null) config.setFaviconUrl(request.faviconUrl());
        if (request.primaryColor() != null) config.setPrimaryColor(request.primaryColor());
        if (request.secondaryColor() != null) config.setSecondaryColor(request.secondaryColor());
        if (request.description() != null) config.setDescription(request.description());
        if (request.heroTitle() != null) config.setHeroTitle(request.heroTitle());
        if (request.heroSubtitle() != null) config.setHeroSubtitle(request.heroSubtitle());
        if (request.phone() != null) config.setPhone(request.phone());
        if (request.email() != null) config.setEmail(request.email());
        if (request.address() != null) config.setAddress(request.address());
        if (request.instagramUrl() != null) config.setInstagramUrl(request.instagramUrl());
        if (request.whatsappNumber() != null) config.setWhatsappNumber(request.whatsappNumber());
        if (request.facebookUrl() != null) config.setFacebookUrl(request.facebookUrl());
        if (request.featuredSectionTitle() != null) config.setFeaturedSectionTitle(request.featuredSectionTitle());
        if (request.featuredSectionSubtitle() != null) config.setFeaturedSectionSubtitle(request.featuredSectionSubtitle());
        if (request.launchesSectionTitle() != null) config.setLaunchesSectionTitle(request.launchesSectionTitle());
        if (request.launchesSectionSubtitle() != null) config.setLaunchesSectionSubtitle(request.launchesSectionSubtitle());
        if (request.categoriesSectionTitle() != null) config.setCategoriesSectionTitle(request.categoriesSectionTitle());
        if (request.categoriesSectionSubtitle() != null) config.setCategoriesSectionSubtitle(request.categoriesSectionSubtitle());
        if (request.servicesSectionTitle() != null) config.setServicesSectionTitle(request.servicesSectionTitle());
        if (request.servicesSectionSubtitle() != null) config.setServicesSectionSubtitle(request.servicesSectionSubtitle());
        if (request.servicesFormTitle() != null) config.setServicesFormTitle(request.servicesFormTitle());
        if (request.servicesFormSubtitle() != null) config.setServicesFormSubtitle(request.servicesFormSubtitle());
        if (request.statsSectionTitle() != null) config.setStatsSectionTitle(request.statsSectionTitle());
        if (request.statsSectionSubtitle() != null) config.setStatsSectionSubtitle(request.statsSectionSubtitle());
        if (request.blogSectionTitle() != null) config.setBlogSectionTitle(request.blogSectionTitle());
        if (request.blogSectionSubtitle() != null) config.setBlogSectionSubtitle(request.blogSectionSubtitle());
        if (request.ctaSectionTitle() != null) config.setCtaSectionTitle(request.ctaSectionTitle());
        if (request.ctaSectionSubtitle() != null) config.setCtaSectionSubtitle(request.ctaSectionSubtitle());
        if (request.aboutPageTitle() != null) config.setAboutPageTitle(request.aboutPageTitle());
        if (request.aboutPageSubtitle() != null) config.setAboutPageSubtitle(request.aboutPageSubtitle());
        if (request.aboutPageDescription() != null) config.setAboutPageDescription(request.aboutPageDescription());
        if (request.aboutMission() != null) config.setAboutMission(request.aboutMission());
        if (request.aboutVision() != null) config.setAboutVision(request.aboutVision());
        if (request.aboutValues() != null) config.setAboutValues(request.aboutValues());
        if (request.aboutFoundedYear() != null) config.setAboutFoundedYear(request.aboutFoundedYear());
        if (request.aboutTeamCount() != null) config.setAboutTeamCount(request.aboutTeamCount());
        if (request.aboutPropertiesCount() != null) config.setAboutPropertiesCount(request.aboutPropertiesCount());

        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website config updated tenant={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse uploadLogo(UUID tenantId, MultipartFile file) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getLogoFid(), config.getLogoUrl());

        FileStorageService.UploadResult uploadResult = fileStorageService.upload(file);
        config.setLogoFid(uploadResult.fid());
        config.setLogoUrl(uploadResult.url());
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website logo uploaded tenant={} fid={}", tenantId, uploadResult.fid());
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse deleteLogo(UUID tenantId) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getLogoFid(), config.getLogoUrl());
        config.setLogoFid(null);
        config.setLogoUrl(null);
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website logo deleted tenant={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse uploadFavicon(UUID tenantId, MultipartFile file) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getFaviconFid(), config.getFaviconUrl());

        FileStorageService.UploadResult uploadResult = fileStorageService.upload(file);
        config.setFaviconFid(uploadResult.fid());
        config.setFaviconUrl(uploadResult.url());
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website favicon uploaded tenant={} fid={}", tenantId, uploadResult.fid());
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse deleteFavicon(UUID tenantId) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getFaviconFid(), config.getFaviconUrl());
        config.setFaviconFid(null);
        config.setFaviconUrl(null);
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website favicon deleted tenant={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse uploadHeroImage(UUID tenantId, MultipartFile file) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getHeroImageFid(), config.getHeroImageUrl());

        FileStorageService.UploadResult uploadResult = fileStorageService.upload(file);
        config.setHeroImageFid(uploadResult.fid());
        config.setHeroImageUrl(uploadResult.url());
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website hero image uploaded tenant={} fid={}", tenantId, uploadResult.fid());
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse deleteHeroImage(UUID tenantId) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getHeroImageFid(), config.getHeroImageUrl());
        config.setHeroImageFid(null);
        config.setHeroImageUrl(null);
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website hero image deleted tenant={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse uploadAboutImage(UUID tenantId, MultipartFile file) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getAboutImageFid(), config.getAboutImageUrl());

        FileStorageService.UploadResult uploadResult = fileStorageService.upload(file);
        config.setAboutImageFid(uploadResult.fid());
        config.setAboutImageUrl(uploadResult.url());
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website about image uploaded tenant={} fid={}", tenantId, uploadResult.fid());
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse deleteAboutImage(UUID tenantId) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        deleteStoredFile(config.getAboutImageFid(), config.getAboutImageUrl());
        config.setAboutImageFid(null);
        config.setAboutImageUrl(null);
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Website about image deleted tenant={}", tenantId);
        return toResponse(saved);
    }

    @Transactional
    public TenantWebsiteConfigResponse updateCustomDomain(UUID tenantId, String customDomain) {
        TenantWebsiteConfig config = getOrCreateConfig(tenantId);
        
        if (customDomain != null && !customDomain.isBlank()) {
            // Validar formato de domínio básico
            if (!isValidDomain(customDomain)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de domínio inválido");
            }
            config.setCustomDomain(customDomain);
        } else {
            config.setCustomDomain(null);
        }
        
        TenantWebsiteConfig saved = tenantWebsiteConfigRepository.save(config);
        log.info("Custom domain updated tenant={} domain={}", tenantId, customDomain);
        return toResponse(saved);
    }

    private boolean isValidDomain(String domain) {
        // Validar domínio: apenas caracteres válidos, sem espaços, deve ter pelo menos um ponto
        String domainRegex = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$";
        return domain.matches(domainRegex) && domain.length() <= 255;
    }

    private TenantWebsiteConfig getOrCreateConfig(UUID tenantId) {
        ensureTenantExists(tenantId);
        return tenantWebsiteConfigRepository.findById(tenantId)
                .orElseGet(() -> {
                    TenantWebsiteConfig config = new TenantWebsiteConfig();
                    config.setTenantId(tenantId);
                    return tenantWebsiteConfigRepository.save(config);
                });
    }

    private void ensureTenantExists(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
    }

    private void deleteStoredFile(String fid, String url) {
        if (fid != null && !fid.isBlank()) {
            fileStorageService.delete(fid, url);
        }
    }

    private TenantWebsiteConfigResponse toResponse(TenantWebsiteConfig config) {
        return new TenantWebsiteConfigResponse(
                config.getCompanyName(),
                config.getLogoUrl(),
                config.getFaviconUrl(),
                config.getPrimaryColor(),
                config.getSecondaryColor(),
                config.getDescription(),
                config.getHeroTitle(),
                config.getHeroSubtitle(),
                config.getPhone(),
                config.getEmail(),
                config.getAddress(),
                config.getInstagramUrl(),
                config.getWhatsappNumber(),
                config.getFacebookUrl(),
                config.getHeroImageUrl(),
                config.getFeaturedSectionTitle(),
                config.getFeaturedSectionSubtitle(),
                config.getLaunchesSectionTitle(),
                config.getLaunchesSectionSubtitle(),
                config.getCategoriesSectionTitle(),
                config.getCategoriesSectionSubtitle(),
                config.getServicesSectionTitle(),
                config.getServicesSectionSubtitle(),
                config.getServicesFormTitle(),
                config.getServicesFormSubtitle(),
                config.getStatsSectionTitle(),
                config.getStatsSectionSubtitle(),
                config.getBlogSectionTitle(),
                config.getBlogSectionSubtitle(),
                config.getCtaSectionTitle(),
                config.getCtaSectionSubtitle(),
                config.getCustomDomain(),
                config.getAboutPageTitle(),
                config.getAboutPageSubtitle(),
                config.getAboutPageDescription(),
                config.getAboutImageUrl(),
                config.getAboutMission(),
                config.getAboutVision(),
                config.getAboutValues(),
                config.getAboutFoundedYear(),
                config.getAboutTeamCount(),
                config.getAboutPropertiesCount()
        );
    }
}
