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
 * Read-only view of the platform-wide customer_addresses table (owned by
 * user-service). ticket-service reads it so the owner New-Booking flow can
 * prefill the structured address fields when picking a customer who already
 * entered an address through the customer app.
 */
@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
public class PlatformCustomerAddress {

    @Id
    private UUID id;

    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "locality")
    private String locality;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "is_default")
    private Boolean isDefault;
}
