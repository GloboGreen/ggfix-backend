package com.repairshop.saas.ticket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.ticket.entity.*;
import com.repairshop.saas.ticket.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors shop-side ticket events into the platform-wide repair_bookings +
 * customer_orders tables that the customer app's "My Orders" feed reads.
 *
 * Why: tickets is per-shop and keyed by customers.id (shop-scoped). The
 * customer app reads customer_orders keyed by customer_users.id (platform).
 * A ticket only reaches the customer's feed when the per-shop customer row
 * carries a platform_user_id link — added by migration 28. This service is
 * the bridge.
 */
@Service
@RequiredArgsConstructor
public class CustomerOrderMirrorService {

    private final TechnicianRepository technicianRepository;
    private final PlatformRepairBookingRepository bookingRepo;
    private final PlatformRepairBookingServiceRepository bookingServiceRepo;
    private final PlatformRepairBookingEventRepository bookingEventRepo;
    private final PlatformCustomerOrderRepository customerOrderRepo;
    private final PlatformCustomerNotificationRepository notificationRepo;
    private final ObjectMapper objectMapper;

    /** Ticket-side status → (booking.status, customer_order.status). */
    private static final Map<String, String[]> STATUS_MAP = Map.of(
            "CREATED",      new String[]{"ORDER_PLACED",     "PENDING"},
            "IN_DIAGNOSIS", new String[]{"IN_DIAGNOSIS",     "PENDING"},
            "QUOTED",       new String[]{"QUOTED",           "PENDING"},
            "APPROVED",     new String[]{"SERVICE_ACCEPTED", "PENDING"},
            "IN_REPAIR",    new String[]{"IN_REPAIR",        "PENDING"},
            "READY",        new String[]{"READY",            "PENDING"},
            "DELIVERED",    new String[]{"DELIVERED",        "COMPLETED"},
            "CANCELLED",    new String[]{"CANCELLED",        "CANCELLED"}
    );

    /** Ticket statuses that imply the technician has actively picked the job up.
     * Mirrors the owner UI's ACCEPTED_STATUSES so a single source decides when
     * to emit TECHNICIAN_ACCEPTED to the customer timeline. */
    private static final java.util.Set<String> ACCEPTED_TICKET_STATUSES = java.util.Set.of(
            "IN_DIAGNOSIS", "IN_REPAIR", "QUOTED", "APPROVED", "READY", "DELIVERED"
    );

    /** Insert (or update) the platform-side booking + customer order for a ticket.
     *  Runs in its OWN transaction (REQUIRES_NEW) so that read-path callers like
     *  TicketService.getEventsForShop can swallow a mirror failure without the
     *  outer transaction being marked rollback-only — otherwise the subsequent
     *  events read would throw UnexpectedRollbackException. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mirrorOnUpsert(Ticket ticket) {
        // platformUserId may be null for walk-in customers. We still create
        // the booking row (with customer_user_id null) so the timeline rail
        // and owner-side history work; the customer-facing feed mirror
        // (customer_orders, notifications) is skipped further down.
        UUID platformUserId = resolvePlatformUserId(ticket);

        String[] mapped = STATUS_MAP.getOrDefault(
                upper(ticket.getStatus()), new String[]{"ORDER_PLACED", "PENDING"});
        String bookingStatus = mapped[0];
        String orderStatus = mapped[1];

        Optional<PlatformRepairBooking> existing = bookingRepo.findByTicketId(ticket.getId());
        PlatformRepairBooking booking = existing.orElseGet(PlatformRepairBooking::new);
        boolean isNew = booking.getId() == null;
        // Snapshot prior state so we can decide which timeline events to emit
        // after the save (the entity fields are about to be overwritten).
        String prevBookingStatus = isNew ? null : booking.getStatus();

        if (isNew) {
            booking.setBookingNumber(makeBookingNumber(ticket));
            booking.setCustomerUserId(platformUserId); // may be null for walk-in
            booking.setShopId(ticket.getShopId());
            booking.setTicketId(ticket.getId());
            booking.setServiceMode("WALK_IN");
        }
        UUID newTechId = ticket.getAssignedTechnicianId();
        Technician tech = newTechId != null
                ? technicianRepository.findById(newTechId).orElse(null)
                : null;
        booking.setTechnicianName(tech != null ? tech.getName() : null);
        // Short display code derived from the tech id (matches the owner UI's
        // "Name - <CODE>" rendering when no explicit code is stored).
        booking.setTechnicianCode(newTechId != null
                ? newTechId.toString().substring(0, 8).toUpperCase()
                : null);
        booking.setBrandId(ticket.getBrandId());
        booking.setModelId(ticket.getModelId());
        booking.setRamOptionId(ticket.getRamOptionId());
        booking.setStorageOptionId(ticket.getStorageOptionId());
        booking.setColor(ticket.getColor());
        booking.setIssueSummary(ticket.getIssueDescription());
        booking.setEstimateAmount(ticket.getEstimatedPrice());
        booking.setFinalAmount(ticket.getFinalPrice());
        booking.setEstimatedReadyAt(ticket.getEstimatedReadyAt());
        booking.setEstimatedDeliveryAt(ticket.getEstimatedDeliveryAt());
        booking.setCustomerApproval(Boolean.TRUE.equals(ticket.getCustomerApproval()) ? "DONE" : null);
        booking.setDevicePin(ticket.getDeviceSecurityValue());
        booking.setMissingDamageParts(formatMissingParts(ticket.getMissingPartsJson()));
        Map<String, String> photos = parseDevicePhotos(ticket.getDevicePhotosJson());
        booking.setFrontImageUrl(photos.get("front"));
        booking.setBackImageUrl(photos.get("back"));
        booking.setVideoUrl(photos.get("video"));
        booking.setStatus(bookingStatus);
        booking = bookingRepo.save(booking);

        rebuildBookingServices(booking.getId(), ticket.getPriceItemsJson(),
                ticket.getRepairServicesSummary());

        emitTimelineEvents(booking.getId(), isNew, prevBookingStatus, bookingStatus,
                newTechId, tech, upper(ticket.getStatus()));

        // Customer-side feed (My Orders) + notifications only apply when the
        // customer has a platform_user_id link. Walk-in tickets stop here —
        // the booking + timeline events were already saved above.
        if (platformUserId == null) return;

        // Orphan recovery: a prior mirror run may have left a customer_orders row
        // pointing at a now-deleted booking. Re-pointing it at the freshly-created
        // booking avoids hitting the order_number unique constraint and preserves
        // the customer_orders id the customer's My Orders list already knows about.
        // Final ref so the .or() lambda can capture it — `booking` itself was
        // reassigned by bookingRepo.save above and is not effectively final.
        final PlatformRepairBooking savedBooking = booking;
        PlatformCustomerOrder co = customerOrderRepo.findByReferenceId(savedBooking.getId())
                .or(() -> customerOrderRepo.findByOrderNumber(savedBooking.getBookingNumber()))
                .orElseGet(PlatformCustomerOrder::new);
        boolean coIsNew = co.getId() == null;
        if (coIsNew) {
            co.setOrderNumber(booking.getBookingNumber());
            co.setCustomerUserId(platformUserId);
            co.setShopId(ticket.getShopId());
            co.setOrderType("REPAIR");
        }
        co.setReferenceId(booking.getId());
        co.setStatus(orderStatus);
        co.setTotalAmount(ticket.getEstimatedPrice());
        co.setPayloadJson(buildPayloadJson(ticket, booking));
        customerOrderRepo.save(co);

        if (isNew) {
            notificationRepo.save(PlatformCustomerNotification.builder()
                    .customerUserId(platformUserId)
                    .bookingId(booking.getId())
                    .bookingNumber(booking.getBookingNumber())
                    .statusKey(bookingStatus)
                    .title("Service booking created")
                    .body("Your booking " + booking.getBookingNumber()
                            + " has been created by the shop.")
                    .type("orders")
                    .isRead(false)
                    .build());
        }
    }

    /**
     * Append the customer-visible timeline events for state changes detected in
     * this mirror call. Keys match the SERVICE_PHASES step keys the customer's
     * Service History screen looks up, so each emitted event lights up the
     * corresponding row with a timestamp.
     */
    private void emitTimelineEvents(UUID bookingId, boolean isNew,
                                    String prevBookingStatus, String bookingStatus,
                                    UUID newTechId,
                                    Technician tech, String ticketStatus) {
        if (isNew) {
            bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                    .bookingId(bookingId).status("BOOKING_CREATED_BY_SHOP")
                    .note("Booking Created by Shop").actor("SHOP").build());
            // Shop creating the booking implies service is accepted in the
            // same step; emit alongside so the timeline rail doesn't skip a row.
            bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                    .bookingId(bookingId).status("SERVICE_ACCEPTED")
                    .note("Service Accepted").actor("SHOP").build());
        } else if (prevBookingStatus != null && !prevBookingStatus.equals(bookingStatus)) {
            // Booking macro status actually changed — emit the corresponding
            // step event from the SHOP_BOOKING_STATUS_OPTIONS list when there
            // is a 1:1 mapping (IN_REPAIR / READY / DELIVERED / CANCELLED
            // are themselves valid step keys). Other transitions are surfaced
            // by their dedicated emits elsewhere.
            String stepKey = mapMacroStatusToStepKey(bookingStatus);
            if (stepKey != null) {
                bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                        .bookingId(bookingId).status(stepKey).actor("SHOP").build());
            }
        }

        String techName = tech != null ? tech.getName() : "technician";

        if (newTechId != null) {
            List<PlatformRepairBookingEvent> existing =
                    bookingEventRepo.findByBookingIdOrderByCreatedAtAsc(bookingId);
            boolean hadAssignBefore = existing.stream().anyMatch(
                    e -> "ASSIGNED_TO_TECHNICIAN".equalsIgnoreCase(e.getStatus())
                            || "REASSIGNED_TO_TECHNICIAN".equalsIgnoreCase(e.getStatus()));
            boolean hasNotAccepted = existing.stream().anyMatch(
                    e -> "AWAITING_TECHNICIAN_ACCEPTANCE".equalsIgnoreCase(e.getStatus()));

            if (!hadAssignBefore) {
                bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                        .bookingId(bookingId).status("ASSIGNED_TO_TECHNICIAN")
                        .note("Assigned to " + techName).actor("SHOP").build());
            }
            // Each (re)assignment puts the booking back into a not-accepted
            // state; emit once so the customer sees the awaiting-acceptance
            // step light up with a timestamp.
            if (!hasNotAccepted) {
                bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                        .bookingId(bookingId).status("AWAITING_TECHNICIAN_ACCEPTANCE")
                        .note("Awaiting Technician Acceptance").actor("SHOP").build());
            }
        }

        if (newTechId != null && ACCEPTED_TICKET_STATUSES.contains(ticketStatus)) {
            List<PlatformRepairBookingEvent> existing =
                    bookingEventRepo.findByBookingIdOrderByCreatedAtAsc(bookingId);
            boolean hasAccepted = existing.stream().anyMatch(
                    e -> "TECHNICIAN_ACCEPTED_SERVICE".equalsIgnoreCase(e.getStatus()));
            boolean hasWorkStarted = existing.stream().anyMatch(
                    e -> "TECHNICIAN_WORK_STARTED".equalsIgnoreCase(e.getStatus()));
            if (!hasAccepted) {
                bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                        .bookingId(bookingId).status("TECHNICIAN_ACCEPTED_SERVICE")
                        .note(techName + " accepted the service").actor("SHOP").build());
            }
            // Accepting the service immediately implies the technician has
            // picked the job up and started work — emit alongside so the
            // two rows on the timeline share the same moment.
            if (!hasWorkStarted) {
                bookingEventRepo.save(PlatformRepairBookingEvent.builder()
                        .bookingId(bookingId).status("TECHNICIAN_WORK_STARTED")
                        .note("Technician Work Started").actor("TECHNICIAN").build());
            }
        }
    }

    /** Map a booking macro status to the step key used by the new timeline,
     *  or null when the macro change is surfaced by a dedicated emit elsewhere. */
    private static String mapMacroStatusToStepKey(String bookingStatus) {
        if (bookingStatus == null) return null;
        switch (bookingStatus.toUpperCase()) {
            case "IN_REPAIR":  return "IN_REPAIR";
            case "READY":      return "READY";
            case "DELIVERED":  return "DELIVERED";
            case "CANCELLED":  return "CANCELLED";
            default:           return null;
        }
    }

    /** Re-build the service rows for a booking from the ticket's priceItemsJson. */
    private void rebuildBookingServices(UUID bookingId, String priceItemsJson, String summary) {
        bookingServiceRepo.deleteByBookingId(bookingId);
        List<Map<String, Object>> items = parsePriceItems(priceItemsJson);
        if (items.isEmpty() && summary != null && !summary.isBlank()) {
            bookingServiceRepo.save(PlatformRepairBookingService.builder()
                    .bookingId(bookingId).serviceName(summary).build());
            return;
        }
        for (Map<String, Object> it : items) {
            Object label = it.get("label");
            Object amount = it.get("amount");
            BigDecimal price = null;
            if (amount != null) {
                try { price = new BigDecimal(amount.toString()); }
                catch (NumberFormatException ignored) {}
            }
            bookingServiceRepo.save(PlatformRepairBookingService.builder()
                    .bookingId(bookingId)
                    .serviceName(label != null ? label.toString() : null)
                    .estimatedPrice(price)
                    .build());
        }
    }

    /** Flatten the ticket's missing parts JSON array into a human-readable string. */
    private String formatMissingParts(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Object> items = objectMapper.readValue(json, new TypeReference<List<Object>>() {});
            if (items == null || items.isEmpty()) return null;
            List<String> labels = new java.util.ArrayList<>();
            for (Object it : items) {
                if (it instanceof Map<?, ?> m) {
                    Object label = ((Map<?, ?>) m).get("label");
                    if (label == null) label = ((Map<?, ?>) m).get("name");
                    if (label != null) labels.add(label.toString());
                } else if (it != null) {
                    labels.add(it.toString());
                }
            }
            return labels.isEmpty() ? null : String.join(", ", labels);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Extract front/back/video URLs from the ticket's device photos JSON object. */
    private Map<String, String> parseDevicePhotos(String json) {
        Map<String, String> out = new HashMap<>();
        if (json == null || json.isBlank()) return out;
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            for (String k : new String[]{"front", "back", "video"}) {
                Object v = raw.get(k);
                if (v != null) out.put(k, v.toString());
            }
        } catch (Exception ignored) {}
        return out;
    }

    private List<Map<String, Object>> parsePriceItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String buildPayloadJson(Ticket t, PlatformRepairBooking b) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Service Booking");
        payload.put("bookingId", b.getId());
        payload.put("ticketId", t.getId());
        payload.put("trackingId", t.getTrackingId());
        payload.put("brandId", t.getBrandId());
        payload.put("modelId", t.getModelId());
        payload.put("serviceMode", "WALK_IN");
        payload.put("deviceName", t.getDeviceDisplayName());
        try { return objectMapper.writeValueAsString(payload); }
        catch (Exception e) { return null; }
    }

    private UUID resolvePlatformUserId(Ticket ticket) {
        // After the customers→customer_users consolidation, tickets.customer_id
        // IS the customer_users.id (no per-shop indirection). Returning it
        // directly is correct; the mirror still treats null as "walk-in
        // without an app account" — the booking row gets created with a
        // null customer_user_id (column was made nullable earlier).
        return ticket.getCustomerId();
    }

    private String makeBookingNumber(Ticket t) {
        String tracking = t.getTrackingId() != null ? t.getTrackingId() : t.getId().toString();
        return "#" + tracking;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase();
    }
}
