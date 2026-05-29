package com.repairshop.saas.masterdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Master-data service exposes nothing tenant-specific — all endpoints are
 * cataloged reference data + media uploads. We allow every request through
 * Spring Security so multipart uploads and admin CRUD don't get blocked by
 * auth side-effects. Real authorization belongs at the API gateway / admin
 * shell, not here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Loud banner so we can tell from the service log that this build is the
        // permissive one (no /media/upload should ever return 403 with this).
        System.out.println("===== master-data-service SecurityConfig: ALL REQUESTS PERMITTED (build v2026-05-27) =====");
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
