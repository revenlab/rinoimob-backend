package com.rinoimob.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_website_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantWebsiteConfig {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "logo_fid")
    private String logoFid;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "favicon_fid")
    private String faviconFid;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "description")
    private String description;

    @Column(name = "hero_title")
    private String heroTitle;

    @Column(name = "hero_subtitle")
    private String heroSubtitle;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "hero_image_fid")
    private String heroImageFid;

    @Column(name = "hero_image_url")
    private String heroImageUrl;

    @Column(name = "featured_section_title")
    private String featuredSectionTitle;

    @Column(name = "featured_section_subtitle")
    private String featuredSectionSubtitle;

    @Column(name = "launches_section_title")
    private String launchesSectionTitle;

    @Column(name = "launches_section_subtitle")
    private String launchesSectionSubtitle;

    @Column(name = "categories_section_title")
    private String categoriesSectionTitle;

    @Column(name = "categories_section_subtitle")
    private String categoriesSectionSubtitle;

    @Column(name = "services_section_title")
    private String servicesSectionTitle;

    @Column(name = "services_section_subtitle")
    private String servicesSectionSubtitle;

    @Column(name = "services_form_title")
    private String servicesFormTitle;

    @Column(name = "services_form_subtitle")
    private String servicesFormSubtitle;

    @Column(name = "stats_section_title")
    private String statsSectionTitle;

    @Column(name = "stats_section_subtitle")
    private String statsSectionSubtitle;

    @Column(name = "blog_section_title")
    private String blogSectionTitle;

    @Column(name = "blog_section_subtitle")
    private String blogSectionSubtitle;

    @Column(name = "cta_section_title")
    private String ctaSectionTitle;

    @Column(name = "cta_section_subtitle")
    private String ctaSectionSubtitle;

    @Column(name = "about_page_title")
    private String aboutPageTitle;

    @Column(name = "about_page_subtitle")
    private String aboutPageSubtitle;

    @Column(name = "about_page_description", columnDefinition = "TEXT")
    private String aboutPageDescription;

    @Column(name = "about_image_fid")
    private String aboutImageFid;

    @Column(name = "about_image_url")
    private String aboutImageUrl;

    @Column(name = "about_mission", columnDefinition = "TEXT")
    private String aboutMission;

    @Column(name = "about_vision", columnDefinition = "TEXT")
    private String aboutVision;

    @Column(name = "about_values", columnDefinition = "TEXT")
    private String aboutValues;

    @Column(name = "about_founded_year")
    private String aboutFoundedYear;

    @Column(name = "about_team_count")
    private String aboutTeamCount;

    @Column(name = "about_properties_count")
    private String aboutPropertiesCount;

    @Column(name = "custom_domain", unique = true)
    private String customDomain;

    @Column(name = "custom_domain_status")
    private String customDomainStatus;

    @Column(name = "custom_domain_provider_id")
    private String customDomainProviderId;

    @Column(name = "custom_domain_target")
    private String customDomainTarget;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
