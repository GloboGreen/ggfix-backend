package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.*;
import com.repairshop.saas.ticket.service.TechnicianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/technicians")
@RequiredArgsConstructor
@Tag(name = "Technicians", description = "Shop employees (technicians) list and manage")
@SecurityRequirement(name = "Bearer")
public class TechnicianController {

    private final TechnicianService technicianService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current technician profile (for logged-in technician)")
    public TechnicianResponse getMe(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.getByUserId(shopId, userId);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current technician profile (name, phone, photo, check-in/out)")
    public TechnicianResponse updateMe(
            @RequestBody UpdateTechnicianRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.updateMe(shopId, userId, body);
    }

    @PostMapping("/me/attendance/check-in")
    @Operation(summary = "Record check-in for today")
    public AttendanceRecordResponse checkIn(
            @RequestBody(required = false) AttendanceCheckRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.recordCheckIn(shopId, userId, body != null ? body.getNotes() : null);
    }

    @PostMapping("/me/attendance/check-out")
    @Operation(summary = "Record check-out for today")
    public AttendanceRecordResponse checkOut(
            @RequestBody(required = false) AttendanceCheckRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.recordCheckOut(shopId, userId, body != null ? body.getNotes() : null);
    }

    @GetMapping("/me/attendance/today")
    @Operation(summary = "Get today's attendance (check-in/check-out)")
    public ResponseEntity<AttendanceRecordResponse> getTodayAttendance(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.getTodayAttendance(shopId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/me/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for leave (current technician)")
    public LeaveRequestResponse createLeaveForMe(
            @Valid @RequestBody CreateLeaveRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing shop or user context");
        return technicianService.createLeaveForMe(shopId, userId, body);
    }

    @GetMapping("/leaves/pending")
    @Operation(summary = "List pending leave requests for the shop (for owner to approve/deny)")
    public List<LeaveRequestResponse> listPendingLeaves(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.listPendingLeavesForShop(shopId);
    }

    /**
     * List shop-wide leave requests filtered by status. Owner uses this for
     * Approved / Rejected tabs on the Leave Requests screen. Static "/leaves"
     * path beats "/{id}" in Spring's matching so the request won't be misread
     * as a per-technician lookup with id="leaves".
     */
    @GetMapping("/leaves")
    @Operation(summary = "List all leave requests for the shop, filtered by status")
    public List<LeaveRequestResponse> listLeavesByStatus(
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.listLeavesForShopByStatus(shopId, status);
    }

    @GetMapping
    @Operation(summary = "List technicians for the shop (for employee management)")
    public List<TechnicianResponse> list(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.listByShop(shopId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create technician (after creating user in auth)")
    public TechnicianResponse create(@Valid @RequestBody CreateTechnicianRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.create(shopId, body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single technician by id (for Edit Profile hydration)")
    public TechnicianResponse getById(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getById(shopId, id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update technician (name, role, availability)")
    public TechnicianResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateTechnicianRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.update(shopId, id, body);
    }

    @GetMapping("/{id}/attendance")
    @Operation(summary = "Get technician attendance for a month")
    public AttendanceSummaryResponse getAttendance(
            @PathVariable UUID id,
            @RequestParam int month,
            @RequestParam int year,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getAttendance(shopId, id, month, year);
    }

    @GetMapping("/{id}/leaves")
    @Operation(summary = "Get technician leave requests (optional month/year filter)")
    public List<LeaveRequestResponse> listLeaves(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getLeaves(shopId, id, month, year);
    }

    @PostMapping("/{id}/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply for leave")
    public LeaveRequestResponse createLeave(
            @PathVariable UUID id,
            @Valid @RequestBody CreateLeaveRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.createLeave(shopId, id, body);
    }

    @GetMapping("/{id}/payslips")
    @Operation(summary = "Get technician payslips for a year")
    public List<PayslipResponse> getPayslips(
            @PathVariable UUID id,
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        if (month != null) {
            return List.of(technicianService.getPayslip(shopId, id, month, year));
        }
        return technicianService.getPayslips(shopId, id, year);
    }

    @GetMapping("/{id}/payslips/{month}/{year}")
    @Operation(summary = "Get single month payslip")
    public PayslipResponse getPayslip(
            @PathVariable UUID id,
            @PathVariable int month,
            @PathVariable int year,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getPayslip(shopId, id, month, year);
    }

    @GetMapping("/{id}/attendance/day")
    @Operation(summary = "Get attendance for a single day (for shift details)")
    public ResponseEntity<AttendanceRecordResponse> getAttendanceDay(
            @PathVariable UUID id,
            @RequestParam LocalDate date,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getAttendanceForDay(shopId, id, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/advances")
    @Operation(summary = "Get recent salary advances")
    public List<SalaryAdvanceResponse> getAdvances(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getAdvances(shopId, id);
    }

    @PostMapping("/{id}/advances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create salary advance")
    public SalaryAdvanceResponse createAdvance(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSalaryAdvanceRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.createAdvance(shopId, id, body);
    }

    @GetMapping("/{id}/experiences")
    @Operation(summary = "Get working record / experiences")
    public List<ExperienceResponse> getExperiences(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.getExperiences(shopId, id);
    }

    @PatchMapping("/{id}/leaves/{leaveId}")
    @Operation(summary = "Approve or reject leave request")
    public LeaveRequestResponse updateLeaveStatus(
            @PathVariable UUID id,
            @PathVariable UUID leaveId,
            @RequestBody LeaveStatusUpdateRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return technicianService.updateLeaveStatus(shopId, id, leaveId, userIdFrom(request), body);
    }

    // Convenience aliases for the documented /approve and /reject paths so the
    // client can hit either /leaves/{id}/approve or PATCH /leaves/{id} with a
    // status body. Both routes funnel into the same service method.
    @PatchMapping("/{id}/leaves/{leaveId}/approve")
    @Operation(summary = "Approve leave request (alias for PATCH /leaves/{id} with status=APPROVED)")
    public LeaveRequestResponse approveLeave(
            @PathVariable UUID id,
            @PathVariable UUID leaveId,
            @RequestBody(required = false) LeaveStatusUpdateRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        LeaveStatusUpdateRequest patch = body != null ? body : new LeaveStatusUpdateRequest();
        patch.setStatus("APPROVED");
        return technicianService.updateLeaveStatus(shopId, id, leaveId, userIdFrom(request), patch);
    }

    @PatchMapping("/{id}/leaves/{leaveId}/reject")
    @Operation(summary = "Reject leave request (alias for PATCH /leaves/{id} with status=REJECTED)")
    public LeaveRequestResponse rejectLeave(
            @PathVariable UUID id,
            @PathVariable UUID leaveId,
            @RequestBody LeaveStatusUpdateRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        body.setStatus("REJECTED");
        return technicianService.updateLeaveStatus(shopId, id, leaveId, userIdFrom(request), body);
    }
}
