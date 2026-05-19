package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Login response with JWT and user info")
public class LoginResponse {

    @Schema(description = "JWT access token")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token expiry in seconds")
    private Long expiresIn;

    @Schema(description = "User ID")
    private String userId;

    @Schema(description = "Shop ID (tenant)")
    private String shopId;

    @Schema(description = "User email")
    private String email;

    @Schema(description = "User display name")
    private String name;

    @Schema(description = "User roles")
    private List<String> roles;
}
