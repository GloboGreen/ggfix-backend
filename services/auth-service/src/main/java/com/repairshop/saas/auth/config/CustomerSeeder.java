package com.repairshop.saas.auth.config;

import com.repairshop.saas.auth.entity.CustomerUser;
import com.repairshop.saas.auth.repository.CustomerUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default customer-app account so the mobile Customer login works
 * out of the box during dev (H2 in-memory profile).
 *
 * Login on the mobile app's Customer tab:
 *   Mobile:   9876543210
 *   Password: test1234
 */
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CustomerSeeder implements CommandLineRunner {

    public static final String DEMO_MOBILE = "9876543210";
    public static final String DEMO_PASSWORD = "test1234";
    public static final String DEMO_EMAIL = "demo.customer@globogreen.local";
    public static final String DEMO_NAME = "Demo Customer";

    private final CustomerUserRepository customerUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (customerUserRepository.existsByMobile(DEMO_MOBILE)) {
            log.info("CustomerSeeder: demo customer already exists (mobile={}).", DEMO_MOBILE);
            return;
        }

        CustomerUser user = CustomerUser.builder()
                .fullName(DEMO_NAME)
                .email(DEMO_EMAIL)
                .mobile(DEMO_MOBILE)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .isActive(true)
                .build();
        customerUserRepository.save(user);

        log.info("CustomerSeeder: created demo customer (mobile={}, password={}).",
                DEMO_MOBILE, DEMO_PASSWORD);
    }
}
