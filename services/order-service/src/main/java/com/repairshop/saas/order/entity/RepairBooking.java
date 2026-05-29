package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "repair_bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepairBooking {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_number", nullable = false, unique = true, length = 60) private String bookingNumber;
    @Column(name = "customer_user_id", nullable = false) private UUID customerUserId;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "ticket_id") private UUID ticketId;
    @Column(name = "saved_device_id") private UUID savedDeviceId;
    @Column(name = "brand_id") private UUID brandId;
    @Column(name = "model_id") private UUID modelId;
    @Column(name = "ram_option_id") private UUID ramOptionId;
    @Column(name = "storage_option_id") private UUID storageOptionId;
    @Column(length = 100) private String color;
    @Column(name = "service_mode", nullable = false, length = 50) private String serviceMode;
    @Column(name = "front_image_url", length = 500) private String frontImageUrl;
    @Column(name = "back_image_url", length = 500) private String backImageUrl;
    @Column(name = "video_url", length = 500) private String videoUrl;
    @Column(name = "issue_summary", columnDefinition = "TEXT") private String issueSummary;
    @Column(name = "estimate_amount", precision = 12, scale = 2) private BigDecimal estimateAmount;
    @Column(name = "final_amount", precision = 12, scale = 2) private BigDecimal finalAmount;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "pickup_address_id") private UUID pickupAddressId;
    @Column(name = "pickup_date") private LocalDate pickupDate;
    @Column(name = "pickup_slot_start") private LocalTime pickupSlotStart;
    @Column(name = "pickup_slot_end") private LocalTime pickupSlotEnd;
    // Service workflow details (populated by shop/technician side; shown on the
    // customer's View Details / Receipt screens).
    @Column(name = "estimated_ready_at") private Instant estimatedReadyAt;
    @Column(name = "estimated_duration_hours") private Integer estimatedDurationHours;
    @Column(name = "estimated_delivery_at") private Instant estimatedDeliveryAt;
    @Column(name = "customer_approval", length = 20) private String customerApproval;
    @Column(name = "device_pin", length = 20) private String devicePin;
    @Column(name = "missing_damage_parts", columnDefinition = "TEXT") private String missingDamageParts;
    @Column(name = "technician_name", length = 120) private String technicianName;
    @Column(name = "technician_code", length = 40) private String technicianCode;
    @Column(name = "technician_photos", columnDefinition = "TEXT") private String technicianPhotos;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ORDER_PLACED";
        if (serviceMode == null) serviceMode = "PICKUP";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
