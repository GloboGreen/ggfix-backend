package com.repairshop.saas.order.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaInitializer {

    private final JdbcTemplate jdbc;

    @Bean
    ApplicationRunner ensureOrderSchema() {
        return args -> {
            addColumnIfMissing("assigned_pickup_person_id", "UUID");
            addColumnIfMissing("pickup_person_name", "VARCHAR(120)");
            addColumnIfMissing("pickup_person_phone", "VARCHAR(30)");
        };
    }

    private void addColumnIfMissing(String columnName, String typeSql) {
        try {
            jdbc.execute("ALTER TABLE repair_bookings ADD COLUMN IF NOT EXISTS " + columnName + " " + typeSql);
        } catch (Exception e) {
            log.warn("Could not ensure repair_bookings.{} exists: {}", columnName, e.getMessage());
        }
    }
}
