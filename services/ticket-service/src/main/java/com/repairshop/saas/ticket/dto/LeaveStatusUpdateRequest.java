package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Update leave status (approve/reject)")
public class LeaveStatusUpdateRequest {

    @Schema(description = "Status: APPROVED, REJECTED", required = true)
    private String status;
}
