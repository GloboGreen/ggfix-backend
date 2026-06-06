package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create a repair note on a ticket")
public class CreateRepairNoteRequest {

    @NotBlank
    @Schema(description = "Free-text note (compliance notes from the tech detail screen)")
    private String note;

    @Schema(description = "Hide from customer? Defaults to false (visible to customer + shop)")
    private Boolean isInternal;
}
