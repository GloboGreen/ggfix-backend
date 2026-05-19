package com.repairshop.saas.auth.service;

import com.repairshop.saas.auth.dto.LoginRequest;
import com.repairshop.saas.auth.dto.LoginResponse;
import com.repairshop.saas.auth.dto.RegisterRequest;
import com.repairshop.saas.auth.dto.RegisterResponse;
import com.repairshop.saas.auth.dto.CreateShopRequest;
import com.repairshop.saas.auth.dto.RegisterTechnicianRequest;
import com.repairshop.saas.auth.dto.ShopResponse;
import com.repairshop.saas.auth.dto.TechnicianResponse;
import com.repairshop.saas.auth.dto.UserResponse;
import com.repairshop.saas.auth.entity.Shop;
import com.repairshop.saas.auth.entity.User;
import com.repairshop.saas.auth.exception.BadRequestException;
import com.repairshop.saas.auth.exception.UnauthorizedException;
import com.repairshop.saas.auth.repository.ShopRepository;
import com.repairshop.saas.auth.repository.UserRepository;
import com.repairshop.saas.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user;
        if (request.getShopSlug() != null && !request.getShopSlug().isBlank()) {
            Shop shop = shopRepository.findBySlug(request.getShopSlug())
                    .orElseThrow(() -> new UnauthorizedException("Invalid shop or credentials"));
            user = userRepository.findByShop_IdAndEmail(shop.getId(), request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("Invalid shop or credentials"));
        } else {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }
        if (!user.getIsActive())
            throw new UnauthorizedException("Account is disabled");
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new UnauthorizedException("Invalid credentials");

        String token = jwtService.generateToken(
                user.getId(),
                user.getShop().getId(),
                user.getEmail(),
                List.of(user.getRole())
        );
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiryMs() / 1000)
                .userId(user.getId().toString())
                .shopId(user.getShop().getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .roles(List.of(user.getRole()))
                .build();
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (shopRepository.existsBySlug(request.getShopSlug()))
            throw new BadRequestException("Shop slug already exists: " + request.getShopSlug());

        Shop shop = Shop.builder()
                .id(UUID.randomUUID())
                .name(request.getShopName())
                .slug(request.getShopSlug())
                .email(request.getEmail())
                .isActive(true)
                .build();
        shop = shopRepository.save(shop);

        if (userRepository.existsByShop_IdAndEmail(shop.getId(), request.getEmail()))
            throw new BadRequestException("Email already registered for this shop");

        User user = User.builder()
                .shop(shop)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName() != null ? request.getName() : request.getEmail())
                .role("SHOP_OWNER")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId().toString())
                .shopId(shop.getId().toString())
                .shopSlug(shop.getSlug())
                .email(user.getEmail())
                .message("Registration successful")
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> listShops() {
        return shopRepository.findAll().stream()
                .map(s -> ShopResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .slug(s.getSlug())
                        .isActive(s.getIsActive())
                        .status(Boolean.TRUE.equals(s.getIsActive()) ? "ACTIVE" : "SUSPENDED")
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersByShop(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        String shopName = shop.getName();
        return userRepository.findByShop_IdOrderByEmailAsc(shopId).stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .name(u.getName())
                        .role(u.getRole())
                        .isActive(u.getIsActive())
                        .shopId(shopId)
                        .shopName(shopName)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianResponse> listTechnicians(UUID shopId) {
        return userRepository.findByShop_IdAndRole(shopId, "TECHNICIAN").stream()
                .map(u -> TechnicianResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .roleLabel("TECHNICIAN")
                        .build())
                .toList();
    }

    @Transactional
    public RegisterResponse registerTechnician(UUID shopId, RegisterTechnicianRequest request) {
        Shop shop = shopRepository.findById(shopId).orElseGet(() -> {
            // Shop may exist in ticket-service but not in auth (e.g. after auth DB reset). Create stub so technician can be registered.
            String slug = "shop-" + shopId.toString().replace("-", "");
            if (shopRepository.existsBySlug(slug))
                slug = "shop-" + shopId.toString();
            return shopRepository.save(Shop.builder()
                    .id(shopId)
                    .name("Shop")
                    .slug(slug)
                    .isActive(true)
                    .build());
        });
        if (userRepository.existsByShop_IdAndEmail(shop.getId(), request.getEmail()))
            throw new BadRequestException("Email already registered for this shop");

        User user = User.builder()
                .shop(shop)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName() != null ? request.getName() : request.getEmail())
                .role("TECHNICIAN")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId().toString())
                .shopId(shop.getId().toString())
                .shopSlug(shop.getSlug())
                .email(user.getEmail())
                .message("Technician added")
                .build();
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request) {
        String slug = request.getSlug().trim().toLowerCase().replaceAll("\\s+", "-");
        if (shopRepository.existsBySlug(slug))
            throw new BadRequestException("Shop slug already exists: " + slug);
        Shop shop = Shop.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .slug(slug)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .isActive(true)
                .build();
        shop = shopRepository.save(shop);
        return ShopResponse.builder()
                .id(shop.getId())
                .name(shop.getName())
                .slug(shop.getSlug())
                .isActive(true)
                .status("ACTIVE")
                .build();
    }

    @Transactional
    public ShopResponse updateShopStatus(UUID shopId, String status) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        boolean active = "ACTIVE".equalsIgnoreCase(status);
        shop.setIsActive(active);
        shop = shopRepository.save(shop);
        return ShopResponse.builder()
                .id(shop.getId())
                .name(shop.getName())
                .slug(shop.getSlug())
                .isActive(shop.getIsActive())
                .status(Boolean.TRUE.equals(shop.getIsActive()) ? "ACTIVE" : "SUSPENDED")
                .build();
    }
}
