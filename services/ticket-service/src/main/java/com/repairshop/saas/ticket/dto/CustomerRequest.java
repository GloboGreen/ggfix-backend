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
@Schema(description = "Create or update customer")
public class CustomerRequest {

    @NotBlank
    @Schema(description = "Customer name", required = true)
    private String name;

    @Schema(description = "Email")
    private String email;

    @NotBlank
    @Schema(description = "Phone number", required = true)
    private String phone;

    @Schema(description = "Full address (state, district, area, street, pincode, etc.)")
    private String address;
}
