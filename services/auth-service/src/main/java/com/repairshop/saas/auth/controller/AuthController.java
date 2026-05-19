package com.repairshop.saas.auth.controller;

import com.repairshop.saas.auth.dto.CreateShopRequest;
import com.repairshop.saas.auth.dto.LoginRequest;
import com.repairshop.saas.auth.dto.LoginResponse;
import com.repairshop.saas.auth.dto.RegisterRequest;
import com.repairshop.saas.auth.dto.RegisterResponse;
import com.repairshop.saas.auth.dto.RegisterTechnicianRequest;
import com.repairshop.saas.auth.dto.ShopResponse;
import com.repairshop.saas.auth.dto.TechnicianResponse;
import com.repairshop.saas.auth.dto.UserResponse;
import com.repairshop.saas.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
