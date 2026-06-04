package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformRepairBookingService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformRepairBookingServiceRepository extends JpaRepository<PlatformRepairBookingService, UUID> {
    void deleteByBookingId(UUID bookingId);
}
