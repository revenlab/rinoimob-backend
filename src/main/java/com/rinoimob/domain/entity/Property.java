package com.rinoimob.domain.entity;

import com.rinoimob.domain.enums.PropertyCondition;
import com.rinoimob.domain.enums.PropertyOperation;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.PropertyType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Column(length = 3, nullable = false)
    private String currency = "BRL";

    @Column(precision = 14, scale = 2)
    private BigDecimal taxes;

    @Column(name = "condo_fee", precision = 14, scale = 2)
    private BigDecimal condoFee;

    @Column(name = "area_total", precision = 10, scale = 2)
    private BigDecimal areaTotal;

    @Column(name = "area_useful", precision = 10, scale = 2)
    private BigDecimal areaUseful;

    private Integer bedrooms;
    private Integer suites;
    private Integer bathrooms;
    private Integer parking;

    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_number", length = 20)
    private String addressNumber;

    @Column(name = "address_complement", length = 100)
    private String addressComplement;

    @Column(name = "address_neighborhood", length = 100)
    private String addressNeighborhood;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_country", length = 2, nullable = false)
    private String addressCountry = "BR";

    @Column(name = "address_zip", length = 20)
    private String addressZip;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "cover_photo_id")
    private UUID coverPhotoId;

    @Column(name = "reference_code", length = 50)
    private String referenceCode;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", length = 30)
    private PropertyCondition condition;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "property_category_map",
            joinColumns = @JoinColumn(name = "property_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<PropertyCategory> categories = new HashSet<>();

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new HashMap<>();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PropertyPhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<FloorPlan> floorPlans = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
