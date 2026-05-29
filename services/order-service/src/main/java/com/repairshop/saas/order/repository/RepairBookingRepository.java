package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairBookingRepository extends JpaRepository<RepairBooking, UUID> {
    List<RepairBooking> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    List<RepairBooking> findByCustomerUserIdAndStatusOrderByCreatedAtDesc(UUID customerUserId, String status);
    List<RepairBooking> findByShopIdOrderByCreatedAtDesc(UUID shopId);
    Optional<RepairBooking> findByBookingNumber(String bookingNumber);
}
