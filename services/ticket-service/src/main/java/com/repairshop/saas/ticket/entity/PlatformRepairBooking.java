package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Writeable mirror of order-service's repair_bookings. ticket-service inserts
 * a row here when a shop owner creates a ticket for a customer that is linked
 * to a platform customer_users id, so the booking surfaces in the customer
 * app's "My Orders" feed. Only the columns the shop-side fan-out writes are
 * mapped; pickup/customer-side fields stay null.
 */
@Entity
@Table(name = "repair_bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformRepairBooking {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_number", nullable = false, unique = true, length = 60) private String bookingNumber;
    @Column(name = "customer_user_id", nullable = false) private UUID customerUserId;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "ticket_id") private UUID ticketId;
    @Column(name = "assigned_technician_id") private UUID assignedTechnicianId;
    @Column(name = "brand_id") private UUID brandId;
    @Column(name = "model_id") private UUID modelId;
    @Column(name = "ram_option_id") private UUID ramOptionId;
    @Column(name = "storage_option_id") private UUID storageOptionId;
    @Column(length = 100) private String color;
    @Column(name = "service_mode", nullable = false, length = 50) private String serviceMode;
    @Column(name = "issue_summary", columnDefinition = "TEXT") private String issueSummary;
    @Column(name = "estimate_amount", precision = 12, scale = 2) private BigDecimal estimateAmount;
    @Column(name = "final_amount", precision = 12, scale = 2) private BigDecimal finalAmount;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "estimated_ready_at") private Instant estimatedReadyAt;
    @Column(name = "estimated_delivery_at") private Instant estimatedDeliveryAt;
    @Column(name = "customer_approval", length = 20) private String customerApproval;
    @Column(name = "device_pin", length = 20) private String devicePin;
    @Column(name = "missing_damage_parts", columnDefinition = "TEXT") private String missingDamageParts;
    @Column(name = "front_image_url", length = 500) private String frontImageUrl;
    @Column(name = "back_image_url", length = 500) private String backImageUrl;
    @Column(name = "video_url", length = 500) private String videoUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ORDER_PLACED";
        if (serviceMode == null) serviceMode = "WALK_IN";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
