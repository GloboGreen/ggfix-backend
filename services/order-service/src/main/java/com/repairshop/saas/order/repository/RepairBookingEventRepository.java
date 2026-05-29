package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairBookingEventRepository extends JpaRepository<RepairBookingEvent, UUID> {
    List<RepairBookingEvent> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
