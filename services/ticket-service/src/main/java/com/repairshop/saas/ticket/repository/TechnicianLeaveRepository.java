package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianLeave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TechnicianLeaveRepository extends JpaRepository<TechnicianLeave, UUID> {

    List<TechnicianLeave> findByTechnicianIdOrderByRequestedAtDesc(UUID technicianId);

    List<TechnicianLeave> findByTechnicianIdAndStartDateBetweenOrderByRequestedAtDesc(
            UUID technicianId, LocalDate start, LocalDate end);

    java.util.Optional<TechnicianLeave> findByTechnicianIdAndId(UUID technicianId, UUID leaveId);

    List<TechnicianLeave> findByTechnicianIdInAndStatusOrderByRequestedAtDesc(Collection<UUID> technicianIds, String status);
}
