package com.repairshop.saas.auth.dto;

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
@Schema(description = "Login request for platform customer (mobile or email + password)")
public class CustomerLoginRequest {

    @Schema(description = "Mobile number (one of mobile or email required)", example = "+919876543210")
    private String mobile;

    @Schema(description = "Email (one of mobile or email required)", example = "rahul@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", required = true)
    private String password;
}
