package com.repairshop.saas.auth.controller;

import com.repairshop.saas.auth.dto.CreateShopRequest;
import com.repairshop.saas.auth.dto.CustomerAuthResponse;
import com.repairshop.saas.auth.dto.CustomerLoginRequest;
import com.repairshop.saas.auth.dto.CustomerRegisterRequest;
import com.repairshop.saas.auth.dto.LoginRequest;
import com.repairshop.saas.auth.dto.LoginResponse;
import com.repairshop.saas.auth.dto.RegisterRequest;
import com.repairshop.saas.auth.dto.RegisterResponse;
import com.repairshop.saas.auth.dto.RegisterTechnicianRequest;
import com.repairshop.saas.auth.dto.ShopResponse;
import com.repairshop.saas.auth.dto.TechnicianResponse;
import com.repairshop.saas.auth.dto.UserResponse;
import com.repairshop.saas.auth.exception.UnauthorizedException;
import com.repairshop.saas.auth.security.JwtService;
import com.repairshop.saas.auth.service.AuthService;
import com.repairshop.saas.auth.service.CustomerAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login, registration, shops, technicians")
public class AuthController {

    private final AuthService authService;
    private final CustomerAuthService customerAuthService;
    private final JwtService jwtService;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Login", description = "Returns JWT and user info")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register", description = "Register new shop and owner")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/shops")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "List shops", description = "List all shops (for admin / assigning technicians)")
    public List<ShopResponse> listShops() {
        return authService.listShops();
    }

    @PostMapping("/shops")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create shop", description = "Create a new shop (admin)")
    public ShopResponse createShop(@Valid @RequestBody CreateShopRequest request) {
        return authService.createShop(request);
    }

    @PatchMapping("/shops/{shopId}/status")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update shop status", description = "Activate or suspend shop")
    public ShopResponse updateShopStatus(@PathVariable UUID shopId, @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        if (status == null || status.isBlank())
            throw new IllegalArgumentException("status is required (ACTIVE or SUSPENDED)");
        return authService.updateShopStatus(shopId, status);
    }

    @GetMapping("/shops/{shopId}/users")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "List users", description = "List all users for a shop (user management)")
    public List<UserResponse> listUsersByShop(@PathVariable UUID shopId) {
        return authService.listUsersByShop(shopId);
    }

    @GetMapping("/shops/{shopId}/technicians")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "List technicians", description = "List technicians for a shop (for assignment)")
    public List<TechnicianResponse> listTechnicians(@PathVariable UUID shopId) {
        return authService.listTechnicians(shopId);
    }

    @PostMapping("/shops/{shopId}/technicians")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add technician", description = "Add a technician user to a shop")
    public RegisterResponse addTechnician(@PathVariable UUID shopId,
                                         @Valid @RequestBody RegisterTechnicianRequest request) {
        return authService.registerTechnician(shopId, request);
    }

    // =========================================================================
    // Platform customer authentication (mobile app)
    // =========================================================================

    @PostMapping("/customer-register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Customer register",
            description = "Register a new platform customer (mobile app). Returns JWT.")
    public CustomerAuthResponse customerRegister(@Valid @RequestBody CustomerRegisterRequest request) {
        return customerAuthService.register(request);
    }

    @PostMapping("/customer-login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Customer login",
            description = "Login platform customer by mobile or email. Returns JWT.")
    public CustomerAuthResponse customerLogin(@Valid @RequestBody CustomerLoginRequest request) {
        return customerAuthService.login(request);
    }

    @GetMapping("/customer-me")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Current customer profile",
            description = "Returns the customer profile for the JWT-bearer. No new token issued.")
    public CustomerAuthResponse customerMe(HttpServletRequest httpRequest) {
        UUID customerUserId = extractCustomerUserId(httpRequest);
        return customerAuthService.me(customerUserId);
    }

    private UUID extractCustomerUserId(HttpServletRequest httpRequest) {
        // Prefer userId previously set by an auth filter (request attribute).
        Object attr = httpRequest.getAttribute("userId");
        if (attr instanceof UUID uuid) return uuid;
        if (attr instanceof String s && !s.isBlank()) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        // Fallback: parse Authorization: Bearer <jwt>
        String header = httpRequest.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
            throw new UnauthorizedException("Missing or invalid Authorization header");
        String token = header.substring("Bearer ".length()).trim();
        try {
            return jwtService.getUserId(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }
}
