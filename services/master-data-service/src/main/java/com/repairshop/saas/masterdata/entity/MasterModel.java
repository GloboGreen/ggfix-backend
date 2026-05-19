package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_models", uniqueConstraints = @UniqueConstraint(columnNames = { "brand_id", "name" }))
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

    /**
     * Optional public URL or path for a hero image of the device / spare part.
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Optional base64-encoded image (PNG/JPEG) for device or spare part.
     * When set, mobile uses data:image/png;base64,{imageBase64} for dropdowns and lists.
     */
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    /**
     * Simple category flag so the UI can distinguish between full devices vs spare parts.
     * Examples: DEVICE, SPARE_PART. Stored as uppercase string for flexibility.
     */
    @Column(name = "category", length = 50)
    private String category;
}
