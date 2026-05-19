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
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Schema(description = "User email", example = "owner@greenmobiles.com", required = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "********", required = true)
    private String password;

    @Schema(description = "Shop slug for tenant context (optional if email is globally unique)")
    private String shopSlug;
}
