package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Leave request for an employee")
public class LeaveRequestResponse {

    @Schema(description = "Leave request ID")
    private UUID id;

    @Schema(description = "Start date")
    private LocalDate startDate;

    @Schema(description = "End date")
    private LocalDate endDate;

    @Schema(description = "Reason")
    private String reason;

    @Schema(description = "Status: PROCESSING, APPROVED, REJECTED")
    private String status;

    @Schema(description = "Requested at")
    private Instant requestedAt;

    @Schema(description = "Applied days description e.g. 1 Day, 2nd Half")
    private String appliedDaysLabel;

    @Schema(description = "Technician ID (for shop pending list)")
    private UUID technicianId;

    @Schema(description = "Technician name (for shop pending list)")
    private String technicianName;
}
