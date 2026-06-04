package com.repairshop.saas.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Read-only view of the platform-wide customer_users table (owned by
 * auth-service / user-service). ticket-service reads it to let the owner
 * New-Booking search find customers who registered through the customer app
 * but have not yet booked at this shop.
 */
@Entity
@Table(name = "customer_users")
@Getter
@Setter
@NoArgsConstructor
public class PlatformCustomerUser {

    @Id
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    private String email;

    private String mobile;

    @Column(name = "is_active")
    private Boolean isActive;
}
