package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_booking_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformRepairBookingEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false, length = 100) private String status;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(length = 100) private String actor;
    // updatable=true so emitOrUpdateBookingEvent can refresh the timestamp
    // on re-submit. The customer/owner timeline rail uses createdAt to find
    // "the latest action", so a stale value would visually omit a fresh tap.
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
