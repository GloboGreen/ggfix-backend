package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "master_models",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_model_brand_name", columnNames = { "brand_id", "name" }),
                @UniqueConstraint(name = "uq_model_series_slug", columnNames = { "series_id", "slug" })
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(nullable = false, length = 255)
    private String name;

    /** SEO-friendly slug, unique within (series_id). */
    @Column(length = 180)
    private String slug;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    /**
     * Free-form classification label for the UI (e.g. DEVICE / SPARE_PART).
     */
    @Column(name = "category", length = 50)
    private String category;

    /** Optional FK -> master_device_categories.id. */
    @Column(name = "category_id")
    private UUID categoryId;

    /** Optional FK -> master_device_series.id. */
    @Column(name = "series_id")
    private UUID seriesId;
}
