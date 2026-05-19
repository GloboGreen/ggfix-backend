package com.repairshop.saas.auth.config;

import com.repairshop.saas.auth.entity.Shop;
import com.repairshop.saas.auth.entity.User;
import com.repairshop.saas.auth.repository.ShopRepository;
import com.repairshop.saas.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Seeds E2E sample data for the Admin shop (H2 dev only). */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    /** Fixed UUID for E2E so ticket-service can seed tickets for this shop. */
    public static final UUID E2E_ADMIN_SHOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String ADMIN_SHOP_SLUG = "admin";
    private static final String ADMIN_EMAIL = "barani";
    private static final String ADMIN_PASSWORD = "barani";
    private static final String TECH_EMAIL = "tech1@admin.com";
    private static final String TECH_PASSWORD = "tech123";
    private static final String CUSTOMER_EMAIL = "customer@test.com";
    private static final String CUSTOMER_PASSWORD = "cust123";

    // E2E test users for mobile app (H2 dev profile: created on auth-service startup)
    // Login: customer / test, owner / test, technician / test
    private static final String E2E_PASSWORD = "test";
    private static final String E2E_CUSTOMER_EMAIL = "customer";
    private static final String E2E_OWNER_EMAIL = "owner";
    private static final String E2E_TECHNICIAN_EMAIL = "technician";

    /** Two extra shops for admin user management (each with one SHOP_OWNER, password: test). */
    private static final UUID SHOP_ALPHA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SHOP_BETA_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String SHOP_ALPHA_SLUG = "shop-alpha";
    private static final String SHOP_BETA_SLUG = "shop-beta";
    private static final String SHOP_USER_PASSWORD = "test";
    private static final String SHOP1_EMAIL = "shop1";
    private static final String SHOP2_EMAIL = "shop2";

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent())
            return;

        Shop adminShop = shopRepository.findBySlug(ADMIN_SHOP_SLUG).orElseGet(() -> {
            return shopRepository.save(Shop.builder()
                    .id(E2E_ADMIN_SHOP_ID)
                    .name("Green Mobiles (E2E)")
                    .slug(ADMIN_SHOP_SLUG)
                    .email("admin@greenmobiles.local")
                    .phone("+91 9876543210")
                    .address("123 Main St, Chennai 600001")
                    .timezone("Asia/Kolkata")
                    .isActive(true)
                    .build());
        });

        User admin = User.builder()
                .shop(adminShop)
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .name("Barani")
                .role("SUPER_ADMIN")
                .isActive(true)
                .build();
        userRepository.save(admin);

        if (userRepository.findByShop_IdAndEmail(adminShop.getId(), TECH_EMAIL).isEmpty()) {
            User tech = User.builder()
                    .shop(adminShop)
                    .email(TECH_EMAIL)
                    .passwordHash(passwordEncoder.encode(TECH_PASSWORD))
                    .name("Tech One")
                    .role("TECHNICIAN")
                    .isActive(true)
                    .build();
            userRepository.save(tech);
        }

        if (userRepository.findByShop_IdAndEmail(adminShop.getId(), CUSTOMER_EMAIL).isEmpty()) {
            User customer = User.builder()
                    .shop(adminShop)
                    .email(CUSTOMER_EMAIL)
                    .passwordHash(passwordEncoder.encode(CUSTOMER_PASSWORD))
                    .name("Test Customer")
                    .role("CUSTOMER")
                    .isActive(true)
                    .build();
            userRepository.save(customer);
        }

        // E2E test users (H2): customer, owner, technician — password: test
        if (userRepository.findByShop_IdAndEmail(adminShop.getId(), E2E_CUSTOMER_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .shop(adminShop)
                    .email(E2E_CUSTOMER_EMAIL)
                    .passwordHash(passwordEncoder.encode(E2E_PASSWORD))
                    .name("E2E Customer")
                    .role("CUSTOMER")
                    .isActive(true)
                    .build());
        }
        if (userRepository.findByShop_IdAndEmail(adminShop.getId(), E2E_OWNER_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .shop(adminShop)
                    .email(E2E_OWNER_EMAIL)
                    .passwordHash(passwordEncoder.encode(E2E_PASSWORD))
                    .name("E2E Shop Owner")
                    .role("SHOP_OWNER")
                    .isActive(true)
                    .build());
        }
        if (userRepository.findByShop_IdAndEmail(adminShop.getId(), E2E_TECHNICIAN_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .shop(adminShop)
                    .email(E2E_TECHNICIAN_EMAIL)
                    .passwordHash(passwordEncoder.encode(E2E_PASSWORD))
                    .name("E2E Technician")
                    .role("TECHNICIAN")
                    .isActive(true)
                    .build());
        }

        // Two shop users (password: test) for admin user management
        Shop shopAlpha = shopRepository.findById(SHOP_ALPHA_ID).orElseGet(() ->
                shopRepository.save(Shop.builder()
                        .id(SHOP_ALPHA_ID)
                        .name("Shop Alpha")
                        .slug(SHOP_ALPHA_SLUG)
                        .isActive(true)
                        .build()));
        if (userRepository.findByShop_IdAndEmail(shopAlpha.getId(), SHOP1_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .shop(shopAlpha)
                    .email(SHOP1_EMAIL)
                    .passwordHash(passwordEncoder.encode(SHOP_USER_PASSWORD))
                    .name("Shop Alpha Owner")
                    .role("SHOP_OWNER")
                    .isActive(true)
                    .build());
        }

        Shop shopBeta = shopRepository.findById(SHOP_BETA_ID).orElseGet(() ->
                shopRepository.save(Shop.builder()
                        .id(SHOP_BETA_ID)
                        .name("Shop Beta")
                        .slug(SHOP_BETA_SLUG)
                        .isActive(true)
                        .build()));
        if (userRepository.findByShop_IdAndEmail(shopBeta.getId(), SHOP2_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .shop(shopBeta)
                    .email(SHOP2_EMAIL)
                    .passwordHash(passwordEncoder.encode(SHOP_USER_PASSWORD))
                    .name("Shop Beta Owner")
                    .role("SHOP_OWNER")
                    .isActive(true)
                    .build());
        }
    }
}
