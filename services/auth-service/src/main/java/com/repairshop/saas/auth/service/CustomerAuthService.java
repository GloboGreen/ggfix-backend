package com.repairshop.saas.auth.service;

import com.repairshop.saas.auth.dto.CustomerAuthResponse;
import com.repairshop.saas.auth.dto.CustomerLoginRequest;
import com.repairshop.saas.auth.dto.CustomerRegisterRequest;
import com.repairshop.saas.auth.entity.CustomerUser;
import com.repairshop.saas.auth.exception.BadRequestException;
import com.repairshop.saas.auth.exception.UnauthorizedException;
import com.repairshop.saas.auth.repository.CustomerUserRepository;
import com.repairshop.saas.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private static final List<String> CUSTOMER_ROLES = List.of("CUSTOMER");

    private final CustomerUserRepository customerUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerAuthResponse register(CustomerRegisterRequest request) {
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase()
                : null;

        if (mobile == null || mobile.isBlank())
            throw new BadRequestException("Mobile is required");
        if (customerUserRepository.existsByMobile(mobile))
            throw new BadRequestException("Mobile already registered: " + mobile);
        if (email != null && customerUserRepository.existsByEmail(email))
            throw new BadRequestException("Email already registered: " + email);

        CustomerUser user = CustomerUser.builder()
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .email(email)
                .mobile(mobile)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .build();
        user = customerUserRepository.save(user);

        String token = jwtService.issueCustomerToken(user.getId(), CUSTOMER_ROLES);
        return toResponse(user, token);
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponse login(CustomerLoginRequest request) {
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase()
                : null;

        if ((mobile == null || mobile.isBlank()) && (email == null || email.isBlank()))
            throw new BadRequestException("Either mobile or email is required");

        CustomerUser user;
        if (mobile != null && !mobile.isBlank()) {
            user = customerUserRepository.findByMobile(mobile)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        } else {
            user = customerUserRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }

        if (!Boolean.TRUE.equals(user.getIsActive()))
            throw new UnauthorizedException("Account is disabled");
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new UnauthorizedException("Invalid credentials");

        String token = jwtService.issueCustomerToken(user.getId(), CUSTOMER_ROLES);
        return toResponse(user, token);
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponse me(UUID customerUserId) {
        CustomerUser user = customerUserRepository.findById(customerUserId)
                .orElseThrow(() -> new UnauthorizedException("Customer not found"));
        return toResponse(user, null);
    }

    private CustomerAuthResponse toResponse(CustomerUser user, String token) {
        return CustomerAuthResponse.builder()
                .accessToken(token)
                .userId(user.getId().toString())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .roles(CUSTOMER_ROLES)
                .build();
    }
}
