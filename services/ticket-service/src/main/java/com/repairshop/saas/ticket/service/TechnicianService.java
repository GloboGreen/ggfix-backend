package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.*;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.TechnicianAttendance;
import com.repairshop.saas.ticket.entity.TechnicianLeave;
import com.repairshop.saas.ticket.entity.TechnicianSalaryAdvance;
import com.repairshop.saas.ticket.entity.TechnicianExperience;
import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import com.repairshop.saas.ticket.repository.TechnicianAttendanceRepository;
import com.repairshop.saas.ticket.repository.TechnicianLeaveRepository;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.repository.TechnicianSalaryAdvanceRepository;
import com.repairshop.saas.ticket.repository.TechnicianExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TechnicianService {

    private final TechnicianRepository technicianRepository;
    private final TechnicianAttendanceRepository attendanceRepository;
    private final TechnicianLeaveRepository leaveRepository;
    private final TechnicianSalaryAdvanceRepository advanceRepository;
    private final TechnicianExperienceRepository experienceRepository;

    @Transactional(readOnly = true)
    public List<TechnicianResponse> listByShop(UUID shopId) {
        return technicianRepository.findByShopIdOrderByNameAsc(shopId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Current technician profile by JWT userId (must belong to shop from JWT). */
    @Transactional(readOnly = true)
    public TechnicianResponse getByUserId(UUID shopId, UUID userId) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        return toResponse(t);
    }

    /** Self-update: only name, phone, photoUrl, defaultCheckIn, defaultCheckOut. */
    @Transactional
    public TechnicianResponse updateMe(UUID shopId, UUID userId, UpdateTechnicianRequest request) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        if (request.getName() != null) t.setName(request.getName().trim());
        if (request.getPhone() != null) t.setPhone(request.getPhone().trim());
        if (request.getPhotoUrl() != null) t.setPhotoUrl(request.getPhotoUrl().trim());
        if (request.getDefaultCheckIn() != null) t.setDefaultCheckIn(request.getDefaultCheckIn());
        if (request.getDefaultCheckOut() != null) t.setDefaultCheckOut(request.getDefaultCheckOut());
        t = technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse create(UUID shopId, CreateTechnicianRequest request) {
        Technician t = Technician.builder()
                .shopId(shopId)
                .userId(request.getUserId())
                .name(request.getName() != null ? request.getName().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .roleLabel(request.getRoleLabel() != null ? request.getRoleLabel().trim() : null)
                .isAvailable(true)
                .salaryAmount(request.getSalaryAmount() != null ? request.getSalaryAmount().trim() : null)
                .salaryPeriod(request.getSalaryPeriod() != null ? request.getSalaryPeriod().trim() : null)
                .idVerificationType(request.getIdVerificationType() != null ? request.getIdVerificationType().trim() : null)
                .idNumber(request.getIdNumber() != null ? request.getIdNumber().trim() : null)
                .dateOfBirth(request.getDateOfBirth())
                .dateOfJoin(request.getDateOfJoin())
                .defaultCheckIn(request.getDefaultCheckIn())
                .defaultCheckOut(request.getDefaultCheckOut())
                .photoUrl(request.getPhotoUrl() != null ? request.getPhotoUrl().trim() : null)
                .build();
        t = technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public TechnicianResponse update(UUID shopId, UUID id, UpdateTechnicianRequest request) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + id));
        if (request.getName() != null) t.setName(request.getName().trim());
        if (request.getEmail() != null) t.setEmail(request.getEmail().trim());
        if (request.getPhone() != null) t.setPhone(request.getPhone().trim());
        if (request.getRoleLabel() != null) t.setRoleLabel(request.getRoleLabel().trim());
        if (request.getIsAvailable() != null) t.setAvailable(request.getIsAvailable());
        if (request.getSalaryAmount() != null) t.setSalaryAmount(request.getSalaryAmount().trim());
        if (request.getSalaryPeriod() != null) t.setSalaryPeriod(request.getSalaryPeriod().trim());
        if (request.getIdVerificationType() != null) t.setIdVerificationType(request.getIdVerificationType().trim());
        if (request.getIdNumber() != null) t.setIdNumber(request.getIdNumber().trim());
        if (request.getDateOfBirth() != null) t.setDateOfBirth(request.getDateOfBirth());
        if (request.getDateOfJoin() != null) t.setDateOfJoin(request.getDateOfJoin());
        if (request.getDefaultCheckIn() != null) t.setDefaultCheckIn(request.getDefaultCheckIn());
        if (request.getDefaultCheckOut() != null) t.setDefaultCheckOut(request.getDefaultCheckOut());
        if (request.getPhotoUrl() != null) t.setPhotoUrl(request.getPhotoUrl().trim());
        t = technicianRepository.save(t);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse getAttendance(UUID shopId, UUID technicianId, int month, int year) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<TechnicianAttendance> list = attendanceRepository.findByTechnicianIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                t.getId(), start, end);
        int present = (int) list.stream().filter(a -> "GENERAL".equals(a.getStatus()) || "LATE".equals(a.getStatus()) || "PERMISSION".equals(a.getStatus())).count();
        int leave = (int) list.stream().filter(a -> "LEAVE".equals(a.getStatus())).count();
        List<AttendanceRecordResponse> records = list.stream().map(this::toAttendanceRecord).collect(Collectors.toList());
        return AttendanceSummaryResponse.builder()
                .month(month)
                .year(year)
                .presentDays(present)
                .lateHours("0")
                .permissionCount(0)
                .leaveDays(leave)
                .holidayCount(0)
                .dailyRecords(records)
                .build();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getLeaves(UUID shopId, UUID technicianId, Integer month, Integer year) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        List<TechnicianLeave> list;
        if (month != null && year != null) {
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            list = leaveRepository.findByTechnicianIdAndStartDateBetweenOrderByRequestedAtDesc(t.getId(), start, end);
        } else {
            list = leaveRepository.findByTechnicianIdOrderByRequestedAtDesc(t.getId());
        }
        return list.stream().map(this::toLeaveResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> getPayslips(UUID shopId, UUID technicianId, int year) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        List<PayslipResponse> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            result.add(buildPayslip(t, month, year));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(UUID shopId, UUID technicianId, int month, int year) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        return buildPayslip(t, month, year);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecordResponse> getAttendanceForDay(UUID shopId, UUID technicianId, LocalDate date) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        return attendanceRepository.findByTechnicianIdAndAttendanceDate(t.getId(), date)
                .map(this::toAttendanceRecord);
    }

    @Transactional(readOnly = true)
    public List<SalaryAdvanceResponse> getAdvances(UUID shopId, UUID technicianId) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        return advanceRepository.findTop10ByTechnicianIdOrderByRequestedAtDesc(t.getId()).stream()
                .map(this::toAdvanceResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SalaryAdvanceResponse createAdvance(UUID shopId, UUID technicianId, CreateSalaryAdvanceRequest request) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        LocalDate advanceDate = request.getAdvanceDate() != null ? request.getAdvanceDate() : LocalDate.now();
        TechnicianSalaryAdvance a = TechnicianSalaryAdvance.builder()
                .technicianId(t.getId())
                .amount(request.getAmount())
                .advanceDate(advanceDate)
                .status("UNPAID")
                .notes(request.getNotes())
                .build();
        a = advanceRepository.save(a);
        return toAdvanceResponse(a);
    }

    @Transactional(readOnly = true)
    public List<ExperienceResponse> getExperiences(UUID shopId, UUID technicianId) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        return experienceRepository.findByTechnicianIdOrderByJoinDateDesc(t.getId()).stream()
                .map(this::toExperienceResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveRequestResponse createLeave(UUID shopId, UUID technicianId, CreateLeaveRequest request) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        TechnicianLeave leave = TechnicianLeave.builder()
                .technicianId(t.getId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .status("PROCESSING")
                .build();
        leave = leaveRepository.save(leave);
        return toLeaveResponse(leave);
    }

    @Transactional
    public LeaveRequestResponse updateLeaveStatus(UUID shopId, UUID technicianId, UUID leaveId, LeaveStatusUpdateRequest request) {
        Technician t = technicianRepository.findByShopIdAndId(shopId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        TechnicianLeave leave = leaveRepository.findByTechnicianIdAndId(t.getId(), leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + leaveId));
        if (request.getStatus() != null && ("APPROVED".equals(request.getStatus()) || "REJECTED".equals(request.getStatus()))) {
            leave.setStatus(request.getStatus());
            leave = leaveRepository.save(leave);
        }
        return toLeaveResponse(leave, t.getName());
    }

    /** Technician check-in for today. Creates or updates today's attendance with checkInTime. */
    @Transactional
    public AttendanceRecordResponse recordCheckIn(UUID shopId, UUID userId, String notes) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        LocalDate today = LocalDate.now();
        TechnicianAttendance a = attendanceRepository.findByTechnicianIdAndAttendanceDate(t.getId(), today)
                .orElse(TechnicianAttendance.builder()
                        .technicianId(t.getId())
                        .attendanceDate(today)
                        .status("GENERAL")
                        .build());
        a.setCheckInTime(LocalTime.now());
        if (notes != null && !notes.isBlank()) a.setNotes(notes);
        a = attendanceRepository.save(a);
        return toAttendanceRecord(a);
    }

    /** Technician check-out for today. Updates today's attendance with checkOutTime. */
    @Transactional
    public AttendanceRecordResponse recordCheckOut(UUID shopId, UUID userId, String notes) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        LocalDate today = LocalDate.now();
        TechnicianAttendance a = attendanceRepository.findByTechnicianIdAndAttendanceDate(t.getId(), today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in found for today. Check in first."));
        a.setCheckOutTime(LocalTime.now());
        if (notes != null && !notes.isBlank()) a.setNotes(a.getNotes() != null ? a.getNotes() + "; " + notes : notes);
        a = attendanceRepository.save(a);
        return toAttendanceRecord(a);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecordResponse> getTodayAttendance(UUID shopId, UUID userId) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        return attendanceRepository.findByTechnicianIdAndAttendanceDate(t.getId(), LocalDate.now())
                .map(this::toAttendanceRecord);
    }

    @Transactional
    public LeaveRequestResponse createLeaveForMe(UUID shopId, UUID userId, CreateLeaveRequest request) {
        Technician t = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found"));
        return createLeave(shopId, t.getId(), request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listPendingLeavesForShop(UUID shopId) {
        List<Technician> technicians = technicianRepository.findByShopIdOrderByNameAsc(shopId);
        if (technicians.isEmpty()) return new ArrayList<>();
        List<UUID> techIds = technicians.stream().map(Technician::getId).collect(Collectors.toList());
        java.util.Map<UUID, String> nameByTechId = technicians.stream().collect(Collectors.toMap(Technician::getId, t -> t.getName() != null ? t.getName() : "—"));
        return leaveRepository.findByTechnicianIdInAndStatusOrderByRequestedAtDesc(techIds, "PROCESSING").stream()
                .map(l -> toLeaveResponse(l, nameByTechId.getOrDefault(l.getTechnicianId(), "—")))
                .collect(Collectors.toList());
    }

    private PayslipResponse buildPayslip(Technician t, int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<TechnicianAttendance> list = attendanceRepository.findByTechnicianIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                t.getId(), start, end);
        int present = (int) list.stream().filter(a -> "GENERAL".equals(a.getStatus()) || "LATE".equals(a.getStatus()) || "PERMISSION".equals(a.getStatus())).count();
        String salary = t.getSalaryAmount() != null ? t.getSalaryAmount() : "0";
        int gross = parseAmount(salary);
        int net = present > 0 ? (gross * present / 30) : 0; // simplified: proportional by days
        return PayslipResponse.builder()
                .month(month)
                .year(year)
                .periodStart(start)
                .periodEnd(end)
                .presentDays(present)
                .dailyWageDays(present)
                .regularSalary(salary)
                .regularWage("0")
                .netSalary(String.valueOf(net))
                .netWage("0")
                .build();
    }

    private int parseAmount(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private AttendanceRecordResponse toAttendanceRecord(TechnicianAttendance a) {
        String workingHours = "0";
        if (a.getWorkingMinutes() != null && a.getWorkingMinutes() > 0) {
            long h = a.getWorkingMinutes() / 60;
            long m = a.getWorkingMinutes() % 60;
            workingHours = String.format("%02d:%02d:00", h, m);
        } else if (a.getCheckInTime() != null && a.getCheckOutTime() != null) {
            long min = Duration.between(a.getCheckInTime(), a.getCheckOutTime()).toMinutes();
            workingHours = String.format("%02d:%02d:00", min / 60, min % 60);
        }
        String dayLabel = a.getAttendanceDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return AttendanceRecordResponse.builder()
                .date(a.getAttendanceDate())
                .dayLabel(dayLabel)
                .checkInTime(a.getCheckInTime())
                .checkOutTime(a.getCheckOutTime())
                .status(a.getStatus() != null ? a.getStatus() : "GENERAL")
                .workingHours(workingHours)
                .notes(a.getNotes())
                .build();
    }

    private LeaveRequestResponse toLeaveResponse(TechnicianLeave l) {
        return toLeaveResponse(l, null);
    }

    private LeaveRequestResponse toLeaveResponse(TechnicianLeave l, String technicianName) {
        long days = ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1;
        String appliedDaysLabel = days == 1 ? "1 Day" : days + " Days";
        LeaveRequestResponse.LeaveRequestResponseBuilder b = LeaveRequestResponse.builder()
                .id(l.getId())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .reason(l.getReason())
                .status(l.getStatus())
                .requestedAt(l.getRequestedAt())
                .appliedDaysLabel(appliedDaysLabel)
                .technicianId(l.getTechnicianId());
        if (technicianName != null) b.technicianName(technicianName);
        return b.build();
    }

    private TechnicianResponse toResponse(Technician t) {
        return TechnicianResponse.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .name(t.getName())
                .email(t.getEmail())
                .phone(t.getPhone())
                .roleLabel(t.getRoleLabel())
                .isAvailable(t.isAvailable())
                .salaryAmount(t.getSalaryAmount())
                .salaryPeriod(t.getSalaryPeriod())
                .idVerificationType(t.getIdVerificationType())
                .idNumber(t.getIdNumber())
                .dateOfBirth(t.getDateOfBirth())
                .dateOfJoin(t.getDateOfJoin())
                .defaultCheckIn(t.getDefaultCheckIn())
                .defaultCheckOut(t.getDefaultCheckOut())
                .photoUrl(t.getPhotoUrl())
                .build();
    }

    private SalaryAdvanceResponse toAdvanceResponse(TechnicianSalaryAdvance a) {
        return SalaryAdvanceResponse.builder()
                .id(a.getId())
                .amount(a.getAmount())
                .advanceDate(a.getAdvanceDate())
                .status(a.getStatus())
                .requestedAt(a.getRequestedAt())
                .notes(a.getNotes())
                .build();
    }

    private ExperienceResponse toExperienceResponse(TechnicianExperience e) {
        LocalDate end = e.getRelievingDate() != null ? e.getRelievingDate() : LocalDate.now();
        long months = ChronoUnit.MONTHS.between(e.getJoinDate().withDayOfMonth(1), end.withDayOfMonth(1));
        long years = months / 12;
        long remMonths = months % 12;
        String duration = years + " Year(s) " + remMonths + " Month(s)";
        return ExperienceResponse.builder()
                .id(e.getId())
                .shopName(e.getShopName())
                .location(e.getLocation())
                .joinDate(e.getJoinDate())
                .relievingDate(e.getRelievingDate())
                .workingType(e.getWorkingType())
                .lastSalary(e.getLastSalary())
                .totalDuration(duration)
                .totalService(e.getTotalService())
                .completedCount(e.getCompletedCount())
                .returnCount(e.getReturnCount())
                .build();
    }
}
