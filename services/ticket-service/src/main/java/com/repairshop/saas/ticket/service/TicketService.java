package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.CreateRepairNoteRequest;
import com.repairshop.saas.ticket.dto.CreateSolutionPackRequest;
import com.repairshop.saas.ticket.dto.RepairNoteResponse;
import com.repairshop.saas.ticket.dto.SolutionPackResponse;
import com.repairshop.saas.ticket.dto.TicketEventResponse;
import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.entity.PlatformRepairBookingEvent;
import com.repairshop.saas.ticket.entity.RepairNote;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.entity.TicketSolutionPack;
import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import com.repairshop.saas.ticket.repository.MasterTechnicianWorkStatusViewRepository;
import com.repairshop.saas.ticket.repository.PlatformRepairBookingEventRepository;
import com.repairshop.saas.ticket.repository.PlatformRepairBookingRepository;
import com.repairshop.saas.ticket.repository.RepairNoteRepository;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import com.repairshop.saas.ticket.repository.TicketSolutionPackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;
    private final PlatformRepairBookingRepository platformRepairBookingRepository;
    private final PlatformRepairBookingEventRepository platformRepairBookingEventRepository;
    private final RepairNoteRepository repairNoteRepository;
    private final TicketSolutionPackRepository ticketSolutionPackRepository;
    private final MasterTechnicianWorkStatusViewRepository masterWorkStatusRepository;
    private final CustomerOrderMirrorService customerOrderMirrorService;

    private static final String TRACKING_PREFIX = "CSPEN";

    @Transactional
    public TicketResponse getById(UUID shopId, UUID id) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        // Self-healing: walk-in tickets created before the mirror was widened
        // to support null customer_user_id won't have a booking row yet, so
        // step events have no booking_id to attach to. Run the mirror first
        // so it creates the booking + the lifecycle events; then sync fills
        // in any step events implied by current ticket state.
        customerOrderMirrorService.mirrorOnUpsert(t);
        syncStepEventsFromTicketState(t);
        return toResponse(t);
    }

    /**
     * Service timeline for the owner BookingTimelineScreen. Reads from the
     * mirrored repair_booking_events (same table the customer history reads),
     * scoped to a ticket via repair_bookings.ticket_id. Returns an empty list
     * (not 404) when the mirror booking hasn't been created yet so the screen
     * can render its phase skeleton.
     */
    @Transactional
    public List<TicketEventResponse> getEventsForShop(UUID shopId, UUID ticketId) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        // Same self-heal as getById: create the booking mirror if missing so
        // walk-in tickets gain a timeline on first view. Errors here are
        // caught + logged so a bad mirror state doesn't 500 the whole events
        // endpoint — we still try to return whatever events exist.
        try { customerOrderMirrorService.mirrorOnUpsert(t); }
        catch (Exception e) { log.error("mirrorOnUpsert failed for ticket {}", ticketId, e); }
        try { syncStepEventsFromTicketState(t); }
        catch (Exception e) { log.error("syncStepEventsFromTicketState failed for ticket {}", ticketId, e); }
        return platformRepairBookingRepository.findByTicketId(t.getId())
                .map(b -> platformRepairBookingEventRepository
                        .findByBookingIdOrderByCreatedAtAsc(b.getId())
                        .stream()
                        .map(e -> TicketEventResponse.builder()
                                .id(e.getId())
                                .status(e.getStatus())
                                .note(e.getNote())
                                .actor(e.getActor())
                                .createdAt(e.getCreatedAt())
                                .build())
                        .toList())
                .orElse(List.of());
    }

    // Emit every "In Service Process" step event that the ticket's current
    // state implies but that hasn't been written yet. Idempotent — the inner
    // emit helpers check for existing rows. Run on every ticket read so
    // pre-existing tickets self-heal without a separate backfill job.
    private void syncStepEventsFromTicketState(Ticket t) {
        if (t == null) return;
        // SERVICE_ACCEPTED follows BOOKING_CREATED_BY_SHOP for every shop-side
        // booking. Self-heals pre-existing tickets that pre-date the auto-emit.
        emitBookingEvent(t.getId(), "SERVICE_ACCEPTED",
                "Service Accepted", "SHOP");
        String status = t.getStatus() == null ? "" : t.getStatus().toUpperCase();
        if (Set.of("IN_REPAIR", "APPROVED", "READY", "DELIVERED").contains(status)) {
            emitBookingEvent(t.getId(), "TECHNICIAN_WORK_STARTED",
                    "Technician Work Started", "TECHNICIAN");
        }
        if (Set.of("QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED").contains(status)) {
            emitBookingEvent(t.getId(), "WAITING_FOR_CUSTOMER_APPROVAL",
                    "Waiting for Customer Approval", "TECHNICIAN");
        }
        if (hasAtLeastOneUrl(t.getTechnicianPhotosJson())) {
            emitBookingEvent(t.getId(), "TECHNICIAN_UPLOADED_DEVICE_IMAGES",
                    "Technician Uploaded Device Images", "TECHNICIAN");
        }
        // Repair notes / solution packs: peek by existence, no full load.
        boolean hasComplianceNote = repairNoteRepository
                .findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .anyMatch(n -> !Boolean.TRUE.equals(n.getIsInternal()));
        if (hasComplianceNote) {
            emitBookingEvent(t.getId(), "TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED",
                    "Technician Compliance Issue Verified & Updated", "TECHNICIAN");
        }
        boolean hasNewSolutionPack = !ticketSolutionPackRepository
                .findByTicketIdAndPackTypeOrderByCreatedAtDesc(t.getId(), "NEW").isEmpty();
        if (hasNewSolutionPack) {
            emitBookingEvent(t.getId(), "ISSUE_IDENTIFIED",
                    "Issue identified by technician", "TECHNICIAN");
        }
    }

    /**
     * Read a single ticket as the customer who placed it. Ownership is established
     * by ticket.customer_id → customers.platform_user_id == JWT subject. The
     * device security value is masked since the customer already knows their own
     * PIN/pattern and the field is sensitive in transit.
     */
    @Transactional(readOnly = true)
    public TicketResponse getForCustomer(UUID platformUserId, UUID id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        if (t.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket has no customer");
        }
        // tickets.customer_id is now the customer_users.id directly (the old
        // per-shop customers row + indirect platform_user_id link is gone).
        if (!t.getCustomerId().equals(platformUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your ticket");
        }
        return toCustomerResponse(t);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByShop(UUID shopId, String status, String q, Pageable pageable) {
        String normalizedStatus = status != null && !status.isBlank() ? status : null;
        String normalizedQuery = q != null && !q.isBlank() ? q.trim() : null;
        Page<Ticket> page = normalizedQuery != null
                ? ticketRepository.searchByShop(shopId, normalizedStatus, normalizedQuery, pageable)
                : normalizedStatus != null
                        ? ticketRepository.findByShopIdAndStatus(shopId, normalizedStatus, pageable)
                        : ticketRepository.findByShopId(shopId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedTechnician(UUID technicianId, Pageable pageable) {
        return ticketRepository.findByAssignedTechnicianId(technicianId, pageable).map(this::toResponse);
    }

    /** For technician "my tickets": resolve user id to technician id then list assigned tickets. */
    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedUser(UUID userId, Pageable pageable) {
        return technicianRepository.findFirstByUserId(userId)
                .map(tech -> ticketRepository.findByAssignedTechnicianId(tech.getId(), pageable).map(this::toResponse))
                .orElse(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    /**
     * Returns booking/ticket counts for the shop (for owner dashboard).
     * Keys: CREATED, IN_DIAGNOSIS, QUOTED, APPROVED, IN_REPAIR, READY, DELIVERED, CANCELLED, total, assignedCount.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCountsByShop(UUID shopId) {
        Map<String, Long> counts = new HashMap<>();
        String[] statuses = { "CREATED", "IN_DIAGNOSIS", "QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED", "CANCELLED" };
        for (String s : statuses) {
            counts.put(s, ticketRepository.countByShopIdAndStatus(shopId, s));
        }
        counts.put("total", ticketRepository.countByShopId(shopId));
        counts.put("assignedCount", ticketRepository.countByShopIdAndAssignedTechnicianIdNotNull(shopId));
        return counts;
    }

    @Transactional
    public TicketResponse create(UUID shopId, TicketRequest request) {
        String trackingId = generateTrackingId(shopId);
        Ticket ticket = Ticket.builder()
                .shopId(shopId)
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .brandId(request.getBrandId())
                .modelId(request.getModelId())
                .ramOptionId(request.getRamOptionId())
                .storageOptionId(request.getStorageOptionId())
                .color(request.getColor())
                .imei(request.getImei())
                .issueDescription(request.getIssueDescription())
                .estimatedPrice(request.getEstimatedPrice())
                .deviceDisplayName(request.getDeviceDisplayName())
                .deviceImageUrl(request.getDeviceImageUrl())
                .repairServicesSummary(request.getRepairServicesSummary())
                .priceItemsJson(request.getPriceItemsJson())
                .missingPartsJson(request.getMissingPartsJson())
                .devicePhotosJson(request.getDevicePhotosJson())
                .deviceSecurityType(request.getDeviceSecurityType())
                .deviceSecurityValue(request.getDeviceSecurityValue())
                .customerApproval(request.getCustomerApproval())
                .estimatedReadyAt(request.getEstimatedReadyAt())
                .estimatedDeliveryAt(request.getEstimatedDeliveryAt())
                .trackingId(trackingId)
                .status("CREATED")
                .build();
        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse update(UUID shopId, UUID id, TicketRequest request) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        // Snapshot the prior approval state so a re-edit after approval can
        // reset the customer's approval and re-prompt them.
        boolean wasApproved = Boolean.TRUE.equals(ticket.getCustomerApproval());
        ticket.setCustomerId(request.getCustomerId());
        if (request.getCustomerName() != null) ticket.setCustomerName(request.getCustomerName());
        if (request.getCustomerPhone() != null) ticket.setCustomerPhone(request.getCustomerPhone());
        ticket.setBrandId(request.getBrandId());
        ticket.setModelId(request.getModelId());
        ticket.setRamOptionId(request.getRamOptionId());
        ticket.setStorageOptionId(request.getStorageOptionId());
        ticket.setColor(request.getColor());
        ticket.setImei(request.getImei());
        ticket.setIssueDescription(request.getIssueDescription());
        ticket.setEstimatedPrice(request.getEstimatedPrice());
        ticket.setDeviceDisplayName(request.getDeviceDisplayName());
        ticket.setDeviceImageUrl(request.getDeviceImageUrl());
        ticket.setRepairServicesSummary(request.getRepairServicesSummary());
        ticket.setPriceItemsJson(request.getPriceItemsJson());
        ticket.setMissingPartsJson(request.getMissingPartsJson());
        ticket.setDevicePhotosJson(request.getDevicePhotosJson());
        ticket.setDeviceSecurityType(request.getDeviceSecurityType());
        ticket.setDeviceSecurityValue(request.getDeviceSecurityValue());
        ticket.setCustomerApproval(request.getCustomerApproval());
        ticket.setEstimatedReadyAt(request.getEstimatedReadyAt());
        ticket.setEstimatedDeliveryAt(request.getEstimatedDeliveryAt());
        // Re-edit semantics: when the shop edits a ticket the customer had
        // already approved AND the edit didn't carry a fresh approval, treat
        // it as a re-booking — clear the prior approval and refresh the
        // Waiting-for-Approval timeline row so the customer is re-prompted.
        boolean reEditAfterApproval = wasApproved
                && !Boolean.TRUE.equals(request.getCustomerApproval());
        if (reEditAfterApproval) {
            ticket.setCustomerApproval(null);
        }
        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);
        if (reEditAfterApproval) {
            emitOrUpdateBookingEvent(ticket.getId(),
                    "WAITING_FOR_CUSTOMER_APPROVAL",
                    "Booking re-edited — waiting for customer approval",
                    "SHOP");
        }
        return toResponse(ticket);
    }

    @Transactional
    public void updateStatus(UUID shopId, UUID id, String status) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        ticket.setStatus(status);
        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);
        emitStepEventsForTicketStatus(ticket.getId(), status);
    }

    /**
     * Lightweight PATCH that currently supports assigning technicians and a few optional fields
     * from a generic map payload. This is primarily used by the mobile "Assign Technician" flow.
     */
    @Transactional
    public TicketResponse patch(UUID shopId, UUID id, Map<String, Object> body) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        if (body.containsKey("assignedTechnicianId")) {
            Object raw = body.get("assignedTechnicianId");
            if (raw == null || String.valueOf(raw).isBlank()) {
                ticket.setAssignedTechnicianId(null);
            } else {
                UUID value = UUID.fromString(String.valueOf(raw));
                // Resolve to technician id: auth returns user id, ticket stores technicians.id
                Technician tech = technicianRepository.findByShopIdAndId(shopId, value)
                        .or(() -> technicianRepository.findByShopIdAndUserId(shopId, value))
                        .orElse(null);
                if (tech == null) {
                    // Auth may return user id with no technicians row; create one so assignment works
                    tech = technicianRepository.save(Technician.builder()
                            .shopId(shopId)
                            .userId(value)
                            .name("Technician")
                            .build());
                }
                ticket.setAssignedTechnicianId(tech.getId());
            }
        }

        String statusBeforePatch = ticket.getStatus();
        if (body.containsKey("status")) {
            Object raw = body.get("status");
            ticket.setStatus(raw != null ? String.valueOf(raw) : ticket.getStatus());
        }

        // The technician detail screen PATCHes this whenever it adds a new
        // "Your Side Device Image". The screen sends the full JSON array each
        // time, so we just overwrite — no merge logic on the server.
        boolean photosBecameNonEmpty = false;
        if (body.containsKey("technicianPhotosJson")) {
            Object raw = body.get("technicianPhotosJson");
            String newValue = raw != null ? String.valueOf(raw) : null;
            // "Became non-empty" = the PATCH carries at least one URL. We don't
            // care about the prior value here; the booking-side event lookup
            // below dedupes so a re-submit with the same photos won't double-emit.
            photosBecameNonEmpty = hasAtLeastOneUrl(newValue);
            ticket.setTechnicianPhotosJson(newValue);
        }

        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);

        // After the mirror has ensured the booking row exists, drop a
        // TECH_UPLOADED_IMAGES event so the customer/owner Service History
        // screens light up the "Technician uploaded device images" step. This
        // step key matches serviceHistoryPhases.js in repair-shop-mobile and
        // is rendered the same way on the owner side.
        if (photosBecameNonEmpty) {
            emitBookingEvent(ticket.getId(), "TECHNICIAN_UPLOADED_DEVICE_IMAGES",
                    "Technician Uploaded Device Images", "TECHNICIAN");
        }
        // If the patch carried a status change, emit the matching step event(s)
        // (work-started / waiting-for-approval). Use the prior status as a guard
        // so a no-op repaint doesn't churn events.
        if (ticket.getStatus() != null && !ticket.getStatus().equalsIgnoreCase(statusBeforePatch)) {
            emitStepEventsForTicketStatus(ticket.getId(), ticket.getStatus());
        }
        return toResponse(ticket);
    }

    private static boolean hasAtLeastOneUrl(String json) {
        if (json == null) return false;
        String trimmed = json.trim();
        // Cheap parse: any "http" substring inside the JSON array/object means
        // the technician submitted at least one uploaded media URL. Avoids
        // pulling in ObjectMapper for a one-liner check.
        return !trimmed.isEmpty() && !trimmed.equals("[]") && trimmed.contains("http");
    }

    // Idempotent event emit for the customer/owner Service History rail. Looks
    // up the booking mirrored against the ticket; skips if an event with the
    // same status key already exists so re-saves don't double-emit.
    private void emitBookingEvent(UUID ticketId, String statusKey, String note, String actor) {
        platformRepairBookingRepository.findByTicketId(ticketId).ifPresent(booking -> {
            boolean alreadyEmitted = platformRepairBookingEventRepository
                    .findByBookingIdOrderByCreatedAtAsc(booking.getId())
                    .stream()
                    .anyMatch(e -> statusKey.equalsIgnoreCase(e.getStatus()));
            if (alreadyEmitted) return;
            platformRepairBookingEventRepository.save(PlatformRepairBookingEvent.builder()
                    .bookingId(booking.getId())
                    .status(statusKey)
                    .note(note)
                    .actor(actor)
                    .build());
        });
    }

    /** Manual emit endpoint for service-progress checklist rows on the
     *  technician's Ticket Detail screen — Repair Work In Progress, Parts
     *  Required, Parts Replaced, Quality Check Started/Completed, Repair
     *  Completed. Idempotent: re-submitting refreshes the existing row's
     *  note + timestamp instead of inserting a duplicate. */
    private static final java.util.Set<String> ALLOWED_PROGRESS_STEP_KEYS = java.util.Set.of(
            "IN_REPAIR", "PARTS_REQUIRED", "PARTS_REPLACED",
            "QUALITY_CHECK_STARTED", "QUALITY_CHECK_COMPLETED", "REPAIR_COMPLETED",
            "READY", "DELIVERED", "CANCELLED");

    private static final java.util.Set<String> ALLOWED_PROGRESS_ACTORS = java.util.Set.of(
            "TECHNICIAN", "OWNER", "SHOP");

    @Transactional
    public void emitProgressStepEvent(UUID shopId, UUID ticketId, String statusKey, String note, String actor) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        String key = statusKey == null ? "" : statusKey.trim().toUpperCase();
        if (!ALLOWED_PROGRESS_STEP_KEYS.contains(key)) {
            throw new IllegalArgumentException("Status key not allowed: " + key);
        }
        String text = note != null && !note.isBlank() ? note.trim() : defaultProgressLabel(key);
        String a = actor == null ? "" : actor.trim().toUpperCase();
        if (!ALLOWED_PROGRESS_ACTORS.contains(a)) a = "TECHNICIAN";
        emitOrUpdateBookingEvent(t.getId(), key, text, a);
        // The BookingsHistory list reads ticket.status directly, so a work-status
        // event alone (Parts Required, Repair Completed, Delivered to Customer,
        // ...) used to leave the badge stuck on the previous lifecycle status.
        // Resolve the admin-managed master row for this code and advance
        // ticket.status to its `ticket_status` mapping so the list badge moves
        // forward (IN_REPAIR → READY → DELIVERED). Falls through silently when
        // the code isn't in master (defensive — keeps the event write working).
        advanceTicketStatusForWorkCode(t, key);
    }

    // Only advance forward through the lifecycle — never demote a DELIVERED
    // ticket back to IN_REPAIR because an older code was re-submitted, and
    // never override CANCELLED.
    private static final java.util.List<String> LIFECYCLE_ORDER = java.util.List.of(
            "CREATED", "IN_DIAGNOSIS", "QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED");

    private void advanceTicketStatusForWorkCode(Ticket t, String code) {
        if (code == null || code.isBlank()) return;
        masterWorkStatusRepository.findByCodeIgnoreCase(code).ifPresent(row -> {
            String target = row.getTicketStatus() == null ? null
                    : row.getTicketStatus().trim().toUpperCase();
            if (target == null || target.isBlank()) return;
            String current = t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase();
            if (target.equals(current)) return;
            // CANCELLED / RETURNED are terminal — don't overwrite them.
            if ("CANCELLED".equals(current) || "RETURNED".equals(current)) return;
            int currentIdx = LIFECYCLE_ORDER.indexOf(current);
            int targetIdx = LIFECYCLE_ORDER.indexOf(target);
            // Both inside the linear ladder → only move forward.
            if (currentIdx >= 0 && targetIdx >= 0 && targetIdx < currentIdx) return;
            t.setStatus(target);
            ticketRepository.save(t);
            customerOrderMirrorService.mirrorOnUpsert(t);
        });
    }

    private static String defaultProgressLabel(String key) {
        switch (key) {
            case "IN_REPAIR":              return "Repair Work In Progress";
            case "PARTS_REQUIRED":         return "Parts Required";
            case "PARTS_REPLACED":         return "Parts Replaced";
            case "QUALITY_CHECK_STARTED":  return "Quality Check Started";
            case "QUALITY_CHECK_COMPLETED":return "Quality Check Completed";
            case "REPAIR_COMPLETED":       return "Repair Completed";
            case "READY":                  return "Ready for Delivery";
            case "DELIVERED":              return "Delivered to Customer";
            case "CANCELLED":              return "Work Cancelled";
            default:                       return key;
        }
    }

    // Same as emitBookingEvent but refreshes the note text and timestamp on
    // an existing row instead of skipping it. Used for steps whose detail
    // changes on each invocation — e.g. compliance notes (latest note text
    // should display) and re-edit-driven approval requests (latest timestamp).
    private void emitOrUpdateBookingEvent(UUID ticketId, String statusKey, String note, String actor) {
        platformRepairBookingRepository.findByTicketId(ticketId).ifPresent(booking -> {
            var existing = platformRepairBookingEventRepository
                    .findByBookingIdOrderByCreatedAtAsc(booking.getId())
                    .stream()
                    .filter(e -> statusKey.equalsIgnoreCase(e.getStatus()))
                    .findFirst();
            if (existing.isPresent()) {
                PlatformRepairBookingEvent e = existing.get();
                e.setNote(note);
                e.setActor(actor);
                // Refresh the timestamp so the customer/owner timeline rail
                // reflects this as the most recent action — required because
                // the dedup keyed by status would otherwise keep the original
                // (now stale) createdAt.
                e.setCreatedAt(java.time.Instant.now());
                platformRepairBookingEventRepository.save(e);
            } else {
                platformRepairBookingEventRepository.save(PlatformRepairBookingEvent.builder()
                        .bookingId(booking.getId())
                        .status(statusKey)
                        .note(note)
                        .actor(actor)
                        .build());
            }
        });
    }

    // Emit the In-Service-Process step events implied by a ticket status:
    //   IN_REPAIR    → TECH_WORK_STARTED   ("Technician Work Started")
    //   QUOTED       → WAITING_APPROVAL    ("Waiting for Customer Approval")
    //   APPROVED     → also TECH_WORK_STARTED — when the customer approves the
    //                 quote, work resumes; this ensures the timeline reflects it
    //                 even if updateStatus was skipped server-side.
    // Step keys match serviceHistoryPhases.js (repair-shop-mobile).
    private void emitStepEventsForTicketStatus(UUID ticketId, String status) {
        if (status == null) return;
        String s = status.trim().toUpperCase();
        switch (s) {
            case "IN_REPAIR":
            case "APPROVED":
                emitBookingEvent(ticketId, "TECHNICIAN_WORK_STARTED",
                        "Technician Work Started", "TECHNICIAN");
                break;
            case "QUOTED":
                emitBookingEvent(ticketId, "WAITING_FOR_CUSTOMER_APPROVAL",
                        "Waiting for Customer Approval", "TECHNICIAN");
                break;
            default:
                // No step event for CREATED / IN_DIAGNOSIS / READY / DELIVERED /
                // CANCELLED — those map to phase-level transitions instead.
                break;
        }
    }

    private String generateTrackingId(UUID shopId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 10000000);
        return TRACKING_PREFIX + suffix;
    }

    /** Customer-facing read: masks PIN/pattern value, joins technician name+code. */
    private TicketResponse toCustomerResponse(Ticket t) {
        TicketResponse base = toResponse(t);
        base.setDeviceSecurityValue(null);
        if (t.getAssignedTechnicianId() != null) {
            technicianRepository.findById(t.getAssignedTechnicianId()).ifPresent(tech -> {
                base.setAssignedTechnicianName(tech.getName());
                base.setAssignedTechnicianCode(
                        t.getAssignedTechnicianId().toString().substring(0, 8).toUpperCase());
            });
        }
        return base;
    }

    private TicketResponse toResponse(Ticket t) {
        return TicketResponse.builder()
                .id(t.getId())
                .shopId(t.getShopId())
                .customerId(t.getCustomerId())
                .customerName(t.getCustomerName())
                .customerPhone(t.getCustomerPhone())
                .assignedTechnicianId(t.getAssignedTechnicianId())
                .trackingId(t.getTrackingId())
                .brandId(t.getBrandId())
                .modelId(t.getModelId())
                .ramOptionId(t.getRamOptionId())
                .storageOptionId(t.getStorageOptionId())
                .color(t.getColor())
                .status(t.getStatus())
                .estimatedPrice(t.getEstimatedPrice())
                .finalPrice(t.getFinalPrice())
                .issueDescription(t.getIssueDescription())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .deviceDisplayName(t.getDeviceDisplayName())
                .deviceImageUrl(t.getDeviceImageUrl())
                .repairServicesSummary(t.getRepairServicesSummary())
                .priceItemsJson(t.getPriceItemsJson())
                .missingPartsJson(t.getMissingPartsJson())
                .devicePhotosJson(resolveDevicePhotosJson(t))
                .technicianPhotosJson(t.getTechnicianPhotosJson())
                .deviceSecurityType(t.getDeviceSecurityType())
                .deviceSecurityValue(t.getDeviceSecurityValue())
                .customerApproval(t.getCustomerApproval())
                .estimatedReadyAt(t.getEstimatedReadyAt())
                .estimatedDeliveryAt(t.getEstimatedDeliveryAt())
                .build();
    }

    // Customer-created bookings hold device photos in repair_bookings.front/back/video_url
    // columns. When the owner converts that booking into a ticket without copying the
    // photos, ticket.device_photos_json is null and the technician sees nothing. Fall
    // back to the linked booking so the photos surface for both new and pre-existing
    // tickets without a data migration.
    private String resolveDevicePhotosJson(Ticket t) {
        String existing = t.getDevicePhotosJson();
        if (existing != null && !existing.isBlank() && !"{}".equals(existing.trim())) return existing;
        return platformRepairBookingRepository.findByTicketId(t.getId())
                .map(b -> {
                    Map<String, String> photos = new HashMap<>();
                    if (b.getFrontImageUrl() != null) photos.put("front", b.getFrontImageUrl());
                    if (b.getBackImageUrl() != null) photos.put("back", b.getBackImageUrl());
                    if (b.getVideoUrl() != null) photos.put("video", b.getVideoUrl());
                    if (photos.isEmpty()) return existing;
                    try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photos); }
                    catch (Exception e) { return existing; }
                })
                .orElse(existing);
    }

    // ---------- Repair notes ----------------------------------------------

    @Transactional
    public RepairNoteResponse addRepairNote(UUID shopId, UUID ticketId, UUID authorId, CreateRepairNoteRequest body) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        RepairNote saved = repairNoteRepository.save(RepairNote.builder()
                .ticketId(t.getId())
                .authorId(authorId)
                .note(body.getNote())
                .isInternal(Boolean.TRUE.equals(body.getIsInternal()))
                .build());
        // Customer-visible compliance notes light up the "Technician Compliance
        // Issue Verified & Updated" step. Internal-only notes stay off the
        // timeline so the customer doesn't see private shop chatter. The event
        // note carries the technician's actual text so the customer sees what
        // was verified, not just a canned label.
        if (!Boolean.TRUE.equals(body.getIsInternal())) {
            String noteText = body.getNote() != null && !body.getNote().isBlank()
                    ? body.getNote()
                    : "Technician Compliance Issue Verified & Updated";
            emitOrUpdateBookingEvent(t.getId(),
                    "TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED",
                    noteText, "TECHNICIAN");
        }
        return toNoteResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RepairNoteResponse> listRepairNotes(UUID shopId, UUID ticketId) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return repairNoteRepository.findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .map(this::toNoteResponse)
                .toList();
    }

    private RepairNoteResponse toNoteResponse(RepairNote n) {
        return RepairNoteResponse.builder()
                .id(n.getId())
                .ticketId(n.getTicketId())
                .authorId(n.getAuthorId())
                .note(n.getNote())
                .isInternal(n.getIsInternal())
                .createdAt(n.getCreatedAt())
                .build();
    }

    // ---------- Solution packs --------------------------------------------

    @Transactional
    public SolutionPackResponse addSolutionPack(UUID shopId, UUID ticketId, UUID uploadedBy, CreateSolutionPackRequest body) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        String type = body.getPackType() == null ? "NEW" : body.getPackType().trim().toUpperCase();
        if (!"REFERENCE".equals(type) && !"NEW".equals(type)) type = "NEW";
        TicketSolutionPack saved = ticketSolutionPackRepository.save(TicketSolutionPack.builder()
                .ticketId(t.getId())
                .shopId(t.getShopId())
                .packType(type)
                .title(body.getTitle())
                .description(body.getDescription())
                .fileUrl(body.getFileUrl())
                .fileName(body.getFileName())
                .uploadedBy(uploadedBy)
                .brandId(body.getBrandId())
                .modelId(body.getModelId())
                .brandName(body.getBrandName())
                .modelName(body.getModelName())
                .issueCategory(body.getIssueCategory())
                .issueSubcategory(body.getIssueSubcategory())
                .issueCategoryId(body.getIssueCategoryId())
                .issueSubcategoryId(body.getIssueSubcategoryId())
                .filesJson(body.getFilesJson())
                .build());
        // A new (technician-uploaded) solution pack signals the "Issue identified"
        // step on the customer/owner history rail. REFERENCE packs are just the
        // tech viewing existing knowledge-base entries, so they don't count.
        if ("NEW".equals(type)) {
            String note = body.getIssueCategory() != null && !body.getIssueCategory().isBlank()
                    ? "Issue identified: " + body.getIssueCategory()
                            + (body.getIssueSubcategory() != null && !body.getIssueSubcategory().isBlank()
                                    ? " — " + body.getIssueSubcategory() : "")
                    : "Issue identified by technician";
            emitBookingEvent(t.getId(), "ISSUE_IDENTIFIED", note, "TECHNICIAN");
        }
        return toPackResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SolutionPackResponse> searchSolutionPacks(UUID shopId, String packType,
                                                          UUID brandId, UUID modelId,
                                                          UUID issueCategoryId, UUID issueSubcategoryId,
                                                          String issueCategory, String issueSubcategory) {
        String type = packType == null || packType.isBlank() ? null : packType.trim().toUpperCase();
        String cat = issueCategory == null || issueCategory.isBlank() ? null : issueCategory.trim();
        String sub = issueSubcategory == null || issueSubcategory.isBlank() ? null : issueSubcategory.trim();
        return ticketSolutionPackRepository
                .searchByShop(shopId, type, brandId, modelId, issueCategoryId, issueSubcategoryId, cat, sub)
                .stream().map(this::toPackResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SolutionPackResponse> listSolutionPacks(UUID shopId, UUID ticketId, String packType) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        List<TicketSolutionPack> rows = packType == null || packType.isBlank()
                ? ticketSolutionPackRepository.findByTicketIdOrderByCreatedAtDesc(t.getId())
                : ticketSolutionPackRepository.findByTicketIdAndPackTypeOrderByCreatedAtDesc(t.getId(), packType.trim().toUpperCase());
        return rows.stream().map(this::toPackResponse).toList();
    }

    private SolutionPackResponse toPackResponse(TicketSolutionPack p) {
        return SolutionPackResponse.builder()
                .id(p.getId())
                .ticketId(p.getTicketId())
                .shopId(p.getShopId())
                .packType(p.getPackType())
                .title(p.getTitle())
                .description(p.getDescription())
                .fileUrl(p.getFileUrl())
                .fileName(p.getFileName())
                .uploadedBy(p.getUploadedBy())
                .brandId(p.getBrandId())
                .modelId(p.getModelId())
                .brandName(p.getBrandName())
                .modelName(p.getModelName())
                .issueCategory(p.getIssueCategory())
                .issueSubcategory(p.getIssueSubcategory())
                .issueCategoryId(p.getIssueCategoryId())
                .issueSubcategoryId(p.getIssueSubcategoryId())
                .filesJson(p.getFilesJson())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
