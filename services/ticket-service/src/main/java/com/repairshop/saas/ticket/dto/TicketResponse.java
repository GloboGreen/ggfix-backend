package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ticket response")
public class TicketResponse {

    @Schema(description = "Ticket ID")
    private UUID id;

    @Schema(description = "Shop ID")
    private UUID shopId;

    @Schema(description = "Customer ID")
    private UUID customerId;

    @Schema(description = "Customer name (denormalized)")
    private String customerName;

    @Schema(description = "Customer phone (denormalized)")
    private String customerPhone;

    @Schema(description = "Assigned technician ID")
    private UUID assignedTechnicianId;

    @Schema(description = "Tracking ID")
    private String trackingId;

    @Schema(description = "Brand ID")
    private UUID brandId;

    @Schema(description = "Model ID")
    private UUID modelId;

    @Schema(description = "Color")
    private String color;

    @Schema(description = "Status")
    private String status;

    @Schema(description = "Estimated price")
    private BigDecimal estimatedPrice;

    @Schema(description = "Final price")
    private BigDecimal finalPrice;

    @Schema(description = "Issue description")
    private String issueDescription;

    @Schema(description = "Created at")
    private Instant createdAt;

    @Schema(description = "Updated at")
    private Instant updatedAt;

    @Schema(description = "Device display name")
    private String deviceDisplayName;

    @Schema(description = "Device image URL")
    private String deviceImageUrl;

    @Schema(description = "Repair services summary")
    private String repairServicesSummary;

    @Schema(description = "Price items JSON")
    private String priceItemsJson;

    @Schema(description = "Missing parts JSON")
    private String missingPartsJson;

    @Schema(description = "Device photos JSON")
    private String devicePhotosJson;

    @Schema(description = "Device security type")
    private String deviceSecurityType;

    @Schema(description = "Device security value")
    private String deviceSecurityValue;

    @Schema(description = "Customer repair approval flag")
    private Boolean customerApproval;

    @Schema(description = "Estimated ready at")
    private Instant estimatedReadyAt;

    @Schema(description = "Estimated delivery at")
    private Instant estimatedDeliveryAt;
}
