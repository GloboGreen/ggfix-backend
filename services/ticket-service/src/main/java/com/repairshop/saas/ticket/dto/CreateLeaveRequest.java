package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Apply for leave")
public class CreateLeaveRequest {

    @NotNull
    @Schema(description = "Start date", required = true)
    private LocalDate startDate;

    @NotNull
    @Schema(description = "End date", required = true)
    private LocalDate endDate;

    @Schema(description = "Reason")
    private String reason;
}
