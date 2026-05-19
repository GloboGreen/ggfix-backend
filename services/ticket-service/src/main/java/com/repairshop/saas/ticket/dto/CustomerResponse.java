package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Customer response")
public class CustomerResponse {

    @Schema(description = "Customer ID")
    private UUID id;

    @Schema(description = "Customer name")
    private String name;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Phone number")
    private String phone;

    @Schema(description = "Address")
    private String address;

    @Schema(description = "Created at")
    private Instant createdAt;
}
