package com.repairshop.saas.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 50)
    private String mobile;

    @Column(length = 120)
    private String district;

    @Column(length = 120)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(name = "front_image_url", length = 1000)
    private String frontImageUrl;

    @Column(name = "banner_image_url", length = 1000)
    private String bannerImageUrl;

    @Column(name = "gst_certificate_url", length = 1000)
    private String gstCertificateUrl;

    @Column(name = "udyam_certificate_url", length = 1000)
    private String udyamCertificateUrl;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "gst_number", length = 50)
    private String gstNumber;

    @Column(length = 120)
    private String taluk;

    @Column(length = 160)
    private String area;

    @Column(length = 200)
    private String street;

    @Column(length = 50)
    private String timezone;

    @Column(name = "pickup_from_time", length = 16)
    private String pickupFromTime;

    @Column(name = "pickup_to_time", length = 16)
    private String pickupToTime;

    @Column(name = "pickup_distance_km")
    private Integer pickupDistanceKm;

    @Column(name = "pickup_enabled", nullable = false)
    private Boolean pickupEnabled = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (timezone == null || timezone.isBlank()) timezone = "Asia/Kolkata";
        if (isActive == null) isActive = Boolean.TRUE;
        if (pickupEnabled == null) pickupEnabled = Boolean.FALSE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
