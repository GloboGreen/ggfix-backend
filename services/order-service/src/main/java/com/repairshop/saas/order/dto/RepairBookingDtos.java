package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class RepairBookingDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceRow {
        private UUID repairServiceId;
        private String serviceCode;
        private String serviceName;
        private BigDecimal estimatedPrice;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RepairBookingRequest {
        private UUID shopId;
        private UUID savedDeviceId;
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String serviceMode; // ENQUIRY | PICKUP | WALK_IN
        private String frontImageUrl;
        private String backImageUrl;
        private String videoUrl;
        private String issueSummary;
        private List<ServiceRow> services;
        private UUID pickupAddressId;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepairBookingEventResp {
        private UUID id;
        private String status;
        private String note;
        private String actor;
        private Instant createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepairBookingResponse {
        private UUID id;
        private String bookingNumber;
        private UUID shopId;
        private UUID ticketId;
        private UUID savedDeviceId;
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String serviceMode;
        private String frontImageUrl;
        private String backImageUrl;
        private String videoUrl;
        private String issueSummary;
        private BigDecimal estimateAmount;
        private BigDecimal finalAmount;
        private String status;
        private UUID pickupAddressId;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
        private Instant estimatedReadyAt;
        private Integer estimatedDurationHours;
        private Instant estimatedDeliveryAt;
        private String customerApproval;
        private String devicePin;
        private String missingDamageParts;
        private String technicianName;
        private String technicianCode;
        private List<String> technicianPhotos;
        private List<ServiceRow> services;
        private List<RepairBookingEventResp> events;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RescheduleRequest {
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
    }

    // Shop/owner posts a service-timeline status (key matches the customer
    // History steps) with an optional human-readable note.
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShopStatusRequest {
        private String status;
        private String note;
    }
}
