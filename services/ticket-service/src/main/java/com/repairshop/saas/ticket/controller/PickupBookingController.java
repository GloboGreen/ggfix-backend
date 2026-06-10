package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pickup-person feed for the employee app. Lives in ticket-service (not
 * order-service) because the employee JWT is already accepted here, and the
 * shared Postgres database lets us JDBC-query repair_bookings directly.
 *
 * GET /technicians/me/pickup-bookings   — returns repair_bookings rows currently
 *                                         assigned to the calling user as pickup
 *                                         person, scoped to their shop. Optional
 *                                         ?status= filter (e.g. PICKUP_ASSIGNED).
 */
@RestController
@RequestMapping("/technicians")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pickup Bookings", description = "Bookings assigned to the calling pickup person")
@SecurityRequirement(name = "Bearer")
public class PickupBookingController {

    private final TechnicianRepository technicianRepository;
    private final JdbcTemplate jdbc;

    // Diagnostic ping — hit this in the browser to confirm the new controller
    // is loaded into the JVM. If this 200s but /me/pickup-bookings 500s, the
    // problem is in the booking handler. If this also 500s or 404s, the
    // controller class itself isn't being registered.
    @GetMapping("/me/pickup-bookings/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "controller", "PickupBookingController", "ts", System.currentTimeMillis());
    }

    @GetMapping("/me/pickup-bookings")
    @Operation(summary = "List repair bookings currently assigned to the calling pickup person")
    public List<Map<String, Object>> listMyPickupBookings(
            HttpServletRequest request,
            @RequestParam(value = "status", required = false) String status) {
        log.info("pickup-bookings: ENTER request={}", request.getRequestURI());
        UUID shopId, userId;
        try {
            shopId = shopIdFrom(request);
            userId = userIdFrom(request);
            log.info("pickup-bookings: jwt resolved shop={} user={}", shopId, userId);
        } catch (Exception e) {
            log.error("pickup-bookings: jwt attr parse failed: {}", e.getMessage(), e);
            return List.of();
        }
        if (shopId == null || userId == null) {
            log.warn("pickup-bookings: missing shop or user context (shop={}, user={})", shopId, userId);
            return List.of();
        }

        Technician me;
        try {
            me = technicianRepository.findByShopIdAndUserId(shopId, userId).orElse(null);
        } catch (Exception e) {
            log.error("pickup-bookings: technician lookup failed shop={} user={}: {}", shopId, userId, e.getMessage(), e);
            return List.of();
        }
        if (me == null) {
            log.warn("pickup-bookings: no technician row for shop={} user={}", shopId, userId);
            return List.of();
        }
        UUID technicianId = me.getId();
        log.info("pickup-bookings: shop={} tech={} status={}", shopId, technicianId, status);

        // Use only repair_bookings columns. customer_name/customer_mobile are
        // denormalized on this table (schema line 511-512). Address text is
        // fetched per-row below with its own try/catch so a join failure on
        // customer_addresses doesn't fail the whole endpoint.
        // Device labels (brand/model/RAM/storage/image) are pulled via LEFT
        // JOINs on the master tables so the pickup-person screens can render
        // a device card without a second round-trip. LEFT JOIN so a missing
        // ram/storage FK doesn't drop the booking from the list.
        StringBuilder sql = new StringBuilder(
                "SELECT rb.id, rb.booking_number, rb.shop_id, rb.customer_user_id, " +
                        "       rb.service_mode, rb.status, rb.issue_summary, rb.color, " +
                        "       rb.estimate_amount, rb.final_amount, " +
                        "       rb.front_image_url, rb.back_image_url, rb.video_url, " +
                        "       rb.pickup_date, rb.pickup_slot_start, rb.pickup_slot_end, " +
                        "       rb.pickup_address_id, " +
                        "       rb.assigned_pickup_person_id, rb.pickup_person_name, rb.pickup_person_phone, " +
                        "       rb.customer_name, rb.customer_mobile, " +
                        "       rb.created_at, rb.updated_at, " +
                        "       rb.brand_id, rb.model_id, rb.ram_option_id, rb.storage_option_id, " +
                        "       mb.name AS brand_name, " +
                        "       mm.name AS model_name, " +
                        "       mm.image_url AS model_image_url, " +
                        "       mm.image_base64 AS model_image_base64, " +
                        "       mr.label AS ram_label, " +
                        "       ms.label AS storage_label " +
                        "FROM repair_bookings rb " +
                        "LEFT JOIN master_brands mb ON mb.id = rb.brand_id " +
                        "LEFT JOIN master_models mm ON mm.id = rb.model_id " +
                        "LEFT JOIN master_ram_options mr ON mr.id = rb.ram_option_id " +
                        "LEFT JOIN master_storage_options ms ON ms.id = rb.storage_option_id " +
                        "WHERE rb.shop_id = CAST(? AS UUID) " +
                        "  AND rb.assigned_pickup_person_id = CAST(? AS UUID)");
        List<Object> args = new ArrayList<>();
        args.add(shopId.toString());
        args.add(technicianId.toString());
        if (status != null && !status.isBlank()) {
            sql.append(" AND UPPER(rb.status) = ?");
            args.add(status.toUpperCase());
        }
        sql.append(" ORDER BY rb.created_at DESC");

        List<Map<String, Object>> bookings;
        try {
            bookings = jdbc.query(sql.toString(), (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                String idStr = rs.getString("id");
                UUID bookingId = UUID.fromString(idStr);
                row.put("id", idStr);
                row.put("bookingNumber", rs.getString("booking_number"));
                row.put("shopId", rs.getString("shop_id"));
                row.put("customerUserId", rs.getString("customer_user_id"));
                row.put("serviceMode", rs.getString("service_mode"));
                row.put("status", rs.getString("status"));
                row.put("issueSummary", rs.getString("issue_summary"));
                row.put("color", rs.getString("color"));
                row.put("estimateAmount", rs.getBigDecimal("estimate_amount"));
                row.put("finalAmount", rs.getBigDecimal("final_amount"));
                row.put("frontImageUrl", rs.getString("front_image_url"));
                row.put("backImageUrl", rs.getString("back_image_url"));
                row.put("videoUrl", rs.getString("video_url"));
                row.put("pickupDate", rs.getString("pickup_date"));
                row.put("pickupSlotStart", rs.getString("pickup_slot_start"));
                row.put("pickupSlotEnd", rs.getString("pickup_slot_end"));
                row.put("pickupAddressId", rs.getString("pickup_address_id"));
                row.put("assignedPickupPersonId", rs.getString("assigned_pickup_person_id"));
                row.put("pickupPersonName", rs.getString("pickup_person_name"));
                row.put("pickupPersonPhone", rs.getString("pickup_person_phone"));
                row.put("customerName", rs.getString("customer_name"));
                row.put("customerMobile", rs.getString("customer_mobile"));
                row.put("brandId", rs.getString("brand_id"));
                row.put("modelId", rs.getString("model_id"));
                row.put("ramOptionId", rs.getString("ram_option_id"));
                row.put("storageOptionId", rs.getString("storage_option_id"));
                row.put("brandName", rs.getString("brand_name"));
                row.put("modelName", rs.getString("model_name"));
                row.put("deviceImageUrl", rs.getString("model_image_url"));
                row.put("modelImageUrl", rs.getString("model_image_url"));
                row.put("deviceImageBase64", rs.getString("model_image_base64"));
                row.put("modelImageBase64", rs.getString("model_image_base64"));
                row.put("ramLabel", rs.getString("ram_label"));
                row.put("storageLabel", rs.getString("storage_label"));
                Timestamp created = rs.getTimestamp("created_at");
                Timestamp updated = rs.getTimestamp("updated_at");
                row.put("createdAt", created != null ? created.toInstant().toString() : null);
                row.put("updatedAt", updated != null ? updated.toInstant().toString() : null);
                // Address + events are best-effort enrichments — never let them
                // 500 the request.
                String addrId = rs.getString("pickup_address_id");
                row.put("pickupAddressText", addrId != null ? loadAddressText(addrId) : null);
                row.put("services", loadServices(bookingId));
                row.put("events", loadEvents(bookingId));
                return row;
            }, args.toArray());
        } catch (Exception e) {
            log.error("pickup-bookings: query failed for shop={} tech={}: {}", shopId, technicianId, e.getMessage(), e);
            return List.of();
        }

        log.info("pickup-bookings: returning {} booking(s) for tech={}", bookings.size(), technicianId);
        return bookings;
    }

    // Canonical pickup-status keys the pickup person can advance through.
    // Ordered — each value must follow the previous one. PICKED_UP can also
    // come after PICKUP_ON_THE_WAY only (no skipping back).
    //
    // REACHED_SHOP requires a GPS check (pickup person must be within
    // SHOP_RADIUS_METERS of the shop's stored lat/lng). RECEIVED_AT_SHOP is
    // the shop-side hand-off — once it fires the booking belongs to the
    // technician-assignment flow.
    private static final List<String> PICKUP_FLOW = List.of(
            "PICKUP_PERSON_ASSIGNED",
            "PICKUP_ON_THE_WAY",
            "REPAIR_ESTIMATE_PROCESSING",
            "DEVICE_PICKED_UP",
            "REACHED_SHOP",
            "RECEIVED_AT_SHOP"
    );

    // Maximum acceptable distance between the pickup person's GPS reading and
    // the shop's stored coordinates when they tap "Reached Shop". 50m matches
    // a typical shop frontage + GPS accuracy floor.
    private static final double SHOP_RADIUS_METERS = 50.0;

    // Aliases for the legacy `PICKUP_ASSIGNED` status column / event key so
    // existing in-flight bookings (which were saved before the rename) still
    // satisfy the "current is PICKUP_PERSON_ASSIGNED" precondition.
    private static boolean isAssigned(String currentStatus) {
        if (currentStatus == null) return false;
        String s = currentStatus.toUpperCase();
        return s.equals("PICKUP_PERSON_ASSIGNED")
                || s.equals("PICKUP_ASSIGNED")
                || s.equals("PICKUP_REASSIGNED");
    }

    // Position of `status` in PICKUP_FLOW after collapsing legacy aliases
    // (PICKUP_ASSIGNED/REASSIGNED → PICKUP_PERSON_ASSIGNED, PICKED_UP →
    // DEVICE_PICKED_UP). Returns -1 for non-pickup statuses (ORDER_PLACED,
    // PICKUP_REQUESTED, PICKUP_ACCEPTED, ORDER_SERVICE_CONFIRMED). Used to
    // reject backward transitions that would otherwise pollute the timeline.
    private static int pickupFlowIndex(String status) {
        if (status == null) return -1;
        String s = status.toUpperCase();
        if (s.equals("PICKUP_ASSIGNED") || s.equals("PICKUP_REASSIGNED")) s = "PICKUP_PERSON_ASSIGNED";
        if (s.equals("PICKED_UP")) s = "DEVICE_PICKED_UP";
        return PICKUP_FLOW.indexOf(s);
    }

    // Mirror of customer_orders.status for the live pickup macro state. The
    // customer's My Orders → Pickup tab filters/displays on this column, so
    // every pickup transition that progresses or terminates the booking
    // updates the mirror.  COMPLETED is reserved for the DELIVERED hand-off
    // that fires on the ticket side (CustomerOrderMirrorService).
    private static String customerOrderMacroFor(String pickupStatus) {
        if (pickupStatus == null) return null;
        return switch (pickupStatus.toUpperCase()) {
            case "PICKUP_PERSON_ASSIGNED", "PICKUP_ASSIGNED", "PICKUP_REASSIGNED",
                 "PICKUP_ON_THE_WAY", "REPAIR_ESTIMATE_PROCESSING",
                 "DEVICE_PICKED_UP", "PICKED_UP",
                 "REACHED_SHOP", "RECEIVED_AT_SHOP" -> "IN_PROGRESS";
            case "CANCELLED" -> "CANCELLED";
            default -> null;
        };
    }

    // Package-private so the sibling ShopPickupBookingController can reuse
    // the same mirror logic on the shop-staff hand-off path without
    // duplicating the SQL.
    void mirrorCustomerOrderStatus(String bookingNumber, String pickupStatus) {
        if (bookingNumber == null || pickupStatus == null) return;
        String macro = customerOrderMacroFor(pickupStatus);
        if (macro == null) return;
        try {
            jdbc.update(
                    "UPDATE customer_orders SET status = ?, updated_at = now() WHERE order_number = ?",
                    macro, bookingNumber);
        } catch (Exception e) {
            log.warn("mirrorCustomerOrderStatus: failed for booking={} status={}: {}",
                    bookingNumber, pickupStatus, e.getMessage());
        }
    }

    /**
     * Pickup person advances the pickup status one step at a time.
     * Returns a JSON body with the new status on success, or {"error": "…"}
     * with the actual cause on failure (HTTP code reflects the category).
     */
    // No-op echo endpoint to isolate whether the PATCH route + auth + body
    // parsing work at all. Hit this before the real /status endpoint. If THIS
    // 500s, the problem is in Spring's request handling (CORS / Jackson /
    // filter chain) — not in our DB code.
    @PatchMapping("/me/pickup-bookings/{id}/status/echo")
    public ResponseEntity<Map<String, Object>> echoStatusUpdate(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("bookingId", id.toString());
        resp.put("shopAttr", request.getAttribute("shopId"));
        resp.put("userAttr", request.getAttribute("userId"));
        resp.put("body", body == null ? Map.of() : body);
        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/me/pickup-bookings/{id}/status")
    @Operation(summary = "Advance pickup-status for one of the pickup person's bookings")
    public ResponseEntity<Map<String, Object>> updatePickupStatus(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        // Wrap the entire handler so an unexpected throw surfaces as a 200 with
        // an error message rather than an opaque 500 the client can't decode.
        try {
            UUID shopId = shopIdFrom(request);
            UUID userId = userIdFrom(request);
            log.info("pickup-status: ENTER booking={} shop={} user={}", id, shopId, userId);
            if (shopId == null || userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
            }
            Object targetObj = body == null ? null : body.get("status");
            String targetRaw = targetObj == null ? null : targetObj.toString();
            if (targetRaw == null || targetRaw.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
            }
            String target = canonicalPickupStatus(targetRaw.trim().toUpperCase());
            boolean isCancel = target.equals("CANCELLED");
            if (!isCancel && !PICKUP_FLOW.contains(target)) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid status: " + target));
            }

            Technician me = technicianRepository.findByShopIdAndUserId(shopId, userId).orElse(null);
            if (me == null) {
                return ResponseEntity.status(403).body(Map.of("error", "not a technician of this shop"));
            }
            UUID technicianId = me.getId();
            log.info("pickup-status: tech={} target={}", technicianId, target);

            // Load just the few fields we need for authz + transition guard.
            // Also pull the booking's shop_id so the REACHED_SHOP radius check
            // can join to shops.latitude/longitude.
            Map<String, Object> current;
            try {
                current = jdbc.queryForMap(
                        "SELECT rb.status, rb.assigned_pickup_person_id, rb.shop_id, rb.booking_number, " +
                                "rb.customer_user_id, rb.estimate_amount, " +
                                "s.latitude AS shop_latitude, s.longitude AS shop_longitude " +
                                "FROM repair_bookings rb " +
                                "LEFT JOIN shops s ON s.id = rb.shop_id " +
                                "WHERE rb.id = CAST(? AS UUID)",
                        id.toString());
            } catch (Exception e) {
                log.warn("pickup-status: booking lookup failed for {}: {}", id, e.getMessage());
                return ResponseEntity.status(404).body(Map.of("error", "booking not found"));
            }
            String currentStatus = stringFrom(current, "status");
            String assignedStr = stringFrom(current, "assigned_pickup_person_id");
            String bookingShopStr = stringFrom(current, "shop_id");
            String bookingNumber = stringFrom(current, "booking_number");
            String customerUserStr = stringFrom(current, "customer_user_id");
            String estimateAmount = stringFrom(current, "estimate_amount");
            log.info("pickup-status: current={} assigned={} bookingShop={}",
                    currentStatus, assignedStr, bookingShopStr);

            if (assignedStr == null || !technicianId.toString().equalsIgnoreCase(assignedStr)) {
                return ResponseEntity.status(403).body(Map.of("error", "not your pickup",
                        "assignedTo", assignedStr, "you", technicianId.toString()));
            }
            if (bookingShopStr == null || !shopId.toString().equalsIgnoreCase(bookingShopStr)) {
                return ResponseEntity.status(403).body(Map.of("error", "wrong shop"));
            }

            // Defence-in-depth backward-transition guard. The per-target
            // checks below already enforce the legal "previous" state for
            // each step, but older bookings polluted the timeline with
            // backward jumps (e.g. REACHED_SHOP → PICKUP_ON_THE_WAY) when
            // pre-guard callers existed. Reject any transition that would
            // move the booking earlier in PICKUP_FLOW so the event log
            // stays monotonic regardless of which client called us.
            int currentIdx = pickupFlowIndex(currentStatus);
            int targetIdx = PICKUP_FLOW.indexOf(target);
            if (!isCancel && currentIdx >= 0 && targetIdx >= 0 && targetIdx < currentIdx) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "cannot move backward from " + currentStatus + " to " + target));
            }

            // Validate transition order.
            if (!isCancel) {
                if (target.equals("PICKUP_ON_THE_WAY") && !isAssigned(currentStatus)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "cannot move to PICKUP_ON_THE_WAY from " + currentStatus));
                }
                if (target.equals("REPAIR_ESTIMATE_PROCESSING")
                        && !"PICKUP_ON_THE_WAY".equalsIgnoreCase(currentStatus)
                        && !"REPAIR_ESTIMATE_PROCESSING".equalsIgnoreCase(currentStatus)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "cannot move to REPAIR_ESTIMATE_PROCESSING from " + currentStatus));
                }
                if (target.equals("DEVICE_PICKED_UP") && !"REPAIR_ESTIMATE_PROCESSING".equalsIgnoreCase(currentStatus)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "submit repair estimate before marking Device Picked Up"));
                }
                if (target.equals("DEVICE_PICKED_UP") && (estimateAmount == null || estimateAmount.isBlank())) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "estimated repair value is required before Device Picked Up"));
                }
                if (target.equals("REACHED_SHOP") && !isDevicePickedUp(currentStatus)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "cannot move to REACHED_SHOP from " + currentStatus));
                }
                if (target.equals("RECEIVED_AT_SHOP") && !"REACHED_SHOP".equalsIgnoreCase(currentStatus)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "cannot move to RECEIVED_AT_SHOP from " + currentStatus));
                }
            }

            // GPS radius gate for REACHED_SHOP: the pickup person must be
            // within SHOP_RADIUS_METERS of the shop's stored lat/lng. We
            // require the client to send {latitude, longitude} on this
            // transition and reject (422) with the actual distance so the UI
            // can tell them how far off they are.
            Double pickupLat = null, pickupLng = null;
            Integer distanceMeters = null;
            if (target.equals("REACHED_SHOP")) {
                pickupLat = parseDouble(value(body, "latitude"));
                pickupLng = parseDouble(value(body, "longitude"));
                if (pickupLat == null || pickupLng == null) {
                    return ResponseEntity.status(422).body(Map.of(
                            "error", "location required",
                            "code", "LOCATION_REQUIRED",
                            "message", "Enable location and try again."));
                }
                Double shopLat = parseDouble(stringFrom(current, "shop_latitude"));
                Double shopLng = parseDouble(stringFrom(current, "shop_longitude"));
                if (shopLat == null || shopLng == null) {
                    return ResponseEntity.status(422).body(Map.of(
                            "error", "shop location not configured",
                            "code", "SHOP_LOCATION_MISSING",
                            "message", "Shop has no saved coordinates. Ask owner to update shop profile."));
                }
                double meters = haversineMeters(pickupLat, pickupLng, shopLat, shopLng);
                distanceMeters = (int) Math.round(meters);
                if (meters > SHOP_RADIUS_METERS) {
                    return ResponseEntity.status(422).body(Map.of(
                            "error", "out of radius",
                            "code", "OUT_OF_RADIUS",
                            "distanceMeters", distanceMeters,
                            "radiusMeters", (int) SHOP_RADIUS_METERS,
                            "message", "You are " + distanceMeters
                                    + "m away. Reach the shop (within " + (int) SHOP_RADIUS_METERS + "m) to continue."));
                }
            }

            // Update repair_bookings.status (+ the corresponding milestone
            // timestamp). now() works in both Postgres and H2.
            try {
                if (target.equals("REACHED_SHOP")) {
                    jdbc.update(
                            "UPDATE repair_bookings SET status = ?, reached_shop_at = now(), updated_at = now() WHERE id = CAST(? AS UUID)",
                            target, id.toString());
                } else if (target.equals("RECEIVED_AT_SHOP")) {
                    jdbc.update(
                            "UPDATE repair_bookings SET status = ?, received_at_shop_at = now(), updated_at = now() WHERE id = CAST(? AS UUID)",
                            target, id.toString());
                } else {
                    jdbc.update(
                            "UPDATE repair_bookings SET status = ?, updated_at = now() WHERE id = CAST(? AS UUID)",
                            target, id.toString());
                }
            } catch (Exception e) {
                log.error("pickup-status: status UPDATE failed for {}: {}", id, e.getMessage(), e);
                return ResponseEntity.status(500).body(Map.of("error", "update failed: " + e.getMessage()));
            }

            // Append event row. For REACHED_SHOP we also persist the GPS
            // reading and the computed distance so the radius check is
            // auditable. Generate UUID in Java to avoid depending on
            // gen_random_uuid() (which isn't available without pgcrypto in
            // older Postgres versions or in H2 dialects).
            String note = body != null && body.get("note") != null
                    ? String.valueOf(body.get("note"))
                    : labelFor(target);
            UUID eventId = UUID.randomUUID();
            try {
                if (target.equals("REACHED_SHOP") && pickupLat != null && pickupLng != null) {
                    jdbc.update(
                            "INSERT INTO repair_booking_events (id, booking_id, status, note, actor, latitude, longitude, distance_meters, created_at) " +
                                    "VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, ?, ?, now())",
                            eventId.toString(), id.toString(), target, note, "PICKUP_PERSON",
                            pickupLat, pickupLng, distanceMeters);
                } else {
                    jdbc.update(
                            "INSERT INTO repair_booking_events (id, booking_id, status, note, actor, created_at) " +
                                    "VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, now())",
                            eventId.toString(), id.toString(), target, note, "PICKUP_PERSON");
                }
            } catch (Exception e) {
                log.error("pickup-status: event INSERT failed for {}: {}", id, e.getMessage(), e);
                return ResponseEntity.status(500).body(Map.of("error", "event insert failed: " + e.getMessage()));
            }

            // Hand the booking off to the shop's ticket pipeline only when
            // the shop has actually taken the device (RECEIVED_AT_SHOP).
            // REACHED_SHOP just means the pickup person is physically at
            // the shop — the device hasn't been handed over yet, and the
            // shop owner's Bookings History must not jump ahead of that
            // hand-off. Idempotent: re-fires are a no-op once ticket_id is
            // set. Best-effort — failure here logs but does not roll back
            // the status transition the pickup person already completed.
            String mintedTicketId = null;
            if (target.equals("RECEIVED_AT_SHOP")) {
                try {
                    mintedTicketId = mintTicketFromBooking(id, shopId, bookingNumber);
                } catch (Exception e) {
                    log.warn("pickup-status: ticket mint failed for booking={}: {}", id, e.getMessage(), e);
                }
            }

            // Mirror the live macro status into customer_orders so the
            // customer's My Orders → Pickup tab card reflects progress
            // (it was previously stuck at "PENDING" forever because the
            // original /buy / /repair-bookings insert wrote PENDING once
            // and nothing updated it).
            mirrorCustomerOrderStatus(bookingNumber, target);

            // Customer notification — best-effort. Don't fail the request if
            // this throws (e.g. table missing in some dev DB).
            if (customerUserStr != null) {
                try {
                    jdbc.update(
                            "INSERT INTO customer_notifications " +
                                    "(id, customer_user_id, booking_id, booking_number, status_key, title, body, type, is_read, created_at) " +
                                    "VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, 'orders', false, now())",
                            UUID.randomUUID().toString(),
                            customerUserStr,
                            id.toString(),
                            bookingNumber,
                            target,
                            labelFor(target),
                            "Booking " + bookingNumber + " — " + labelFor(target));
                } catch (Exception e) {
                    log.warn("pickup-status: notification INSERT failed for {}: {}", id, e.getMessage());
                }
            }

            log.info("pickup-status: shop={} tech={} booking={} {} -> {} (distance={}m)",
                    shopId, technicianId, id, currentStatus, target, distanceMeters);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("id", id.toString());
            ok.put("status", target);
            ok.put("previousStatus", currentStatus == null ? "" : currentStatus);
            if (distanceMeters != null) ok.put("distanceMeters", distanceMeters);
            if (mintedTicketId != null) ok.put("ticketId", mintedTicketId);
            if (target.equals("REACHED_SHOP") || target.equals("RECEIVED_AT_SHOP")) {
                ok.put("message", target.equals("REACHED_SHOP")
                        ? "Pickup person reached the shop successfully."
                        : "Device received at shop.");
            }
            return ResponseEntity.ok(ok);
        } catch (Exception e) {
            log.error("pickup-status: UNHANDLED for booking={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "internal: " + e.getClass().getSimpleName() + ": " + e.getMessage()
            ));
        }
    }

    // The shop-staff "Mark Received" endpoint lives in the sibling
    // ShopPickupBookingController. It can't live in this class because the
    // class-level @RequestMapping("/technicians") would prepend /technicians
    // to every route here — the original misplacement (which served the
    // endpoint at /technicians/shop/pickup-bookings/.../receive-at-shop
    // instead of /shop/pickup-bookings/.../receive-at-shop) caused the 404
    // the mobile app reported as a 500.

    // Package-private — see ShopPickupBookingController.
    String lookupUserDisplayName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT COALESCE(NULLIF(TRIM(name), ''), NULLIF(TRIM(email), ''), NULLIF(TRIM(phone), '')) " +
                            "FROM users WHERE id = CAST(? AS UUID)",
                    String.class, userId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/me/pickup-bookings/{id}/repair-estimate")
    @Operation(summary = "Read repair estimate data for the pickup person's booking")
    public ResponseEntity<Map<String, Object>> getRepairEstimate(
            HttpServletRequest request,
            @PathVariable UUID id) {
        try {
            BookingAccess access = requireAssignedPickup(request, id);
            return ResponseEntity.ok(estimateResponse(id, access.booking));
        } catch (ResponseStatusException e) {
            return error(e);
        }
    }

    @PatchMapping("/me/pickup-bookings/{id}/repair-estimate/images")
    @Transactional
    @Operation(summary = "Store pickup-person device image URLs on the shared booking")
    public ResponseEntity<Map<String, Object>> updateRepairEstimateImages(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            BookingAccess access = requireAssignedPickup(request, id);
            String front = firstNonBlank(value(body, "frontImageUrl"), value(body, "frontImage"), value(body, "front"));
            String back = firstNonBlank(value(body, "backImageUrl"), value(body, "backImage"), value(body, "back"));
            String video = firstNonBlank(value(body, "videoUrl"), value(body, "fullCoverageVideoUrl"), value(body, "video"));
            if (front == null && back == null && video == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "at least one image url is required"));
            }
            jdbc.update(
                    "UPDATE repair_bookings SET " +
                            "front_image_url = COALESCE(?, front_image_url), " +
                            "back_image_url = COALESCE(?, back_image_url), " +
                            "video_url = COALESCE(?, video_url), " +
                            "updated_at = now() WHERE id = CAST(? AS UUID)",
                    front, back, video, id.toString());
            Map<String, Object> refreshed = loadBookingSnapshot(id);
            log.info("pickup-estimate-images: shop={} tech={} booking={}", access.shopId, access.technicianId, id);
            return ResponseEntity.ok(estimateResponse(id, refreshed));
        } catch (ResponseStatusException e) {
            return error(e);
        } catch (Exception e) {
            log.error("pickup-estimate-images: failed for booking={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "image update failed: " + e.getMessage()));
        }
    }

    @PostMapping("/me/pickup-bookings/{id}/repair-estimate")
    @Transactional
    @Operation(summary = "Submit pickup-person repair estimate on the shared booking")
    public ResponseEntity<Map<String, Object>> submitRepairEstimate(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            BookingAccess access = requireAssignedPickup(request, id);
            String currentStatus = stringFrom(access.booking, "status");
            if (!"PICKUP_ON_THE_WAY".equalsIgnoreCase(currentStatus)
                    && !"REPAIR_ESTIMATE_PROCESSING".equalsIgnoreCase(currentStatus)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "repair estimate can be submitted only after Pickup On The Way"));
            }
            BigDecimal estimate = parseAmount(firstNonBlank(
                    value(body, "estimatedRepairValue"),
                    value(body, "estimateAmount"),
                    value(body, "estimatedAmount"),
                    value(body, "repairEstimatedValue")
            ));
            if (estimate == null || estimate.signum() < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "estimated repair value is required"));
            }
            String front = firstNonBlank(value(body, "frontImageUrl"), value(body, "frontImage"), value(body, "front"));
            String back = firstNonBlank(value(body, "backImageUrl"), value(body, "backImage"), value(body, "back"));
            String video = firstNonBlank(value(body, "videoUrl"), value(body, "fullCoverageVideoUrl"), value(body, "video"));
            String issueSummary = firstNonBlank(value(body, "issueSummary"), value(body, "complaintNotes"), value(body, "note"));

            // Pickup-person-confirmed device taxonomy. The customer's initial
            // booking values are overwritten only when the technician sent a
            // non-blank value — otherwise leave whatever the customer entered.
            UUID brandId = parseUuid(value(body, "brandId"));
            UUID modelId = parseUuid(value(body, "modelId"));
            UUID ramOptionId = parseUuid(value(body, "ramOptionId"));
            UUID storageOptionId = parseUuid(value(body, "storageOptionId"));
            String color = firstNonBlank(value(body, "color"));

            jdbc.update(
                    "UPDATE repair_bookings SET " +
                            "estimate_amount = ?, " +
                            "front_image_url = COALESCE(?, front_image_url), " +
                            "back_image_url = COALESCE(?, back_image_url), " +
                            "video_url = COALESCE(?, video_url), " +
                            "issue_summary = COALESCE(?, issue_summary), " +
                            "brand_id = COALESCE(CAST(? AS UUID), brand_id), " +
                            "model_id = COALESCE(CAST(? AS UUID), model_id), " +
                            "ram_option_id = COALESCE(CAST(? AS UUID), ram_option_id), " +
                            "storage_option_id = COALESCE(CAST(? AS UUID), storage_option_id), " +
                            "color = COALESCE(?, color), " +
                            "status = 'REPAIR_ESTIMATE_PROCESSING', " +
                            "updated_at = now() WHERE id = CAST(? AS UUID)",
                    estimate, front, back, video, issueSummary,
                    brandId != null ? brandId.toString() : null,
                    modelId != null ? modelId.toString() : null,
                    ramOptionId != null ? ramOptionId.toString() : null,
                    storageOptionId != null ? storageOptionId.toString() : null,
                    color,
                    id.toString());

            // Replace the booking's repair_booking_services rows with whatever
            // the pickup person picked on the Device Services screen. Doing it
            // as DELETE-then-INSERT keeps the table in lockstep with the
            // submitted estimate (customer may have selected the wrong issues
            // originally; the technician's picks are now the source of truth).
            Object servicesObj = body == null ? null : body.get("services");
            if (servicesObj instanceof List<?>) {
                try {
                    jdbc.update("DELETE FROM repair_booking_services WHERE booking_id = CAST(? AS UUID)", id.toString());
                    for (Object item : (List<?>) servicesObj) {
                        if (!(item instanceof Map<?, ?>)) continue;
                        Map<?, ?> svc = (Map<?, ?>) item;
                        UUID serviceUuid = parseUuid(stringValueOf(svc.get("serviceId")));
                        if (serviceUuid == null) serviceUuid = parseUuid(stringValueOf(svc.get("repairServiceId")));
                        String code = firstNonBlank(stringValueOf(svc.get("serviceCode")), stringValueOf(svc.get("code")));
                        String name = firstNonBlank(stringValueOf(svc.get("serviceName")), stringValueOf(svc.get("name")));
                        BigDecimal price = parseAmount(stringValueOf(svc.get("price")));
                        if (price == null) price = parseAmount(stringValueOf(svc.get("estimatedPrice")));
                        String warranty = firstNonBlank(stringValueOf(svc.get("warranty")));
                        jdbc.update(
                                "INSERT INTO repair_booking_services " +
                                        "(id, booking_id, repair_service_id, service_code, service_name, estimated_price, warranty, created_at) " +
                                        "VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, now())",
                                UUID.randomUUID().toString(),
                                id.toString(),
                                serviceUuid != null ? serviceUuid.toString() : null,
                                code,
                                name,
                                price,
                                warranty);
                    }
                } catch (Exception e) {
                    log.warn("pickup-estimate: services replace failed for {}: {}", id, e.getMessage());
                }
            }

            String bookingNumber = stringFrom(access.booking, "booking_number");
            if (bookingNumber != null) {
                try {
                    jdbc.update("UPDATE customer_orders SET total_amount = ?, updated_at = now() WHERE order_number = ?",
                            estimate, bookingNumber);
                } catch (Exception e) {
                    log.warn("pickup-estimate: customer_orders sync failed for {}: {}", bookingNumber, e.getMessage());
                }
            }

            String note = "Repair estimate submitted";
            appendEvent(id, "REPAIR_ESTIMATE_PROCESSING", note, "PICKUP_PERSON");
            notifyCustomer(access.booking, id, "REPAIR_ESTIMATE_PROCESSING", "Repair Estimate Processing", note);
            Map<String, Object> refreshed = loadBookingSnapshot(id);
            log.info("pickup-estimate: shop={} tech={} booking={} amount={}",
                    access.shopId, access.technicianId, id, estimate);
            return ResponseEntity.ok(estimateResponse(id, refreshed));
        } catch (ResponseStatusException e) {
            return error(e);
        } catch (Exception e) {
            log.error("pickup-estimate: failed for booking={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "estimate submit failed: " + e.getMessage()));
        }
    }

    // queryForMap returns column-name keys in whatever case the driver hands
    // back (lowercase on Postgres, uppercase on H2). Pull the value tolerantly.
    // Package-private — shared with ShopPickupBookingController.
    static String stringFrom(Map<String, Object> row, String col) {
        if (row == null) return null;
        Object v = row.get(col);
        if (v == null) v = row.get(col.toUpperCase());
        if (v == null) v = row.get(col.toLowerCase());
        return v == null ? null : v.toString();
    }

    private static String labelFor(String code) {
        switch (code) {
            case "PICKUP_REQUESTED":        return "Pickup Requested";
            case "PICKUP_ACCEPTED":         return "Pickup Accepted";
            case "PICKUP_PERSON_ASSIGNED":  return "Pickup Person Assigned";
            case "PICKUP_ON_THE_WAY":       return "Pickup Person On The Way";
            case "REPAIR_ESTIMATE_PROCESSING": return "Repair Estimate Processing";
            case "DEVICE_PICKED_UP":        return "Device Picked Up";
            case "PICKED_UP":               return "Device Picked Up";
            case "REACHED_SHOP":            return "Reached Shop";
            case "RECEIVED_AT_SHOP":        return "Received at Shop";
            case "ESTIMATE_SENT_TO_CUSTOMER": return "Estimate Sent To Customer";
            case "CUSTOMER_APPROVED":       return "Customer Approved";
            case "REPAIR_IN_PROGRESS":      return "Repair In Progress";
            case "REPAIR_COMPLETED":        return "Repair Completed";
            case "READY_FOR_DELIVERY":      return "Ready For Delivery";
            case "CANCELLED":               return "Pickup Cancelled";
            default:                        return code.replace('_', ' ');
        }
    }

    private static String canonicalPickupStatus(String status) {
        if (status == null) return null;
        String s = status.toUpperCase();
        if (s.equals("PICKED_UP") || s.equals("DEVICE_RECEIVED")) return "DEVICE_PICKED_UP";
        if (s.equals("ESTIMATE_PROCESSING") || s.equals("ESTIMATE_SUBMITTED")) return "REPAIR_ESTIMATE_PROCESSING";
        return s;
    }

    private static boolean isDevicePickedUp(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.equals("DEVICE_PICKED_UP") || s.equals("PICKED_UP") || s.equals("DEVICE_RECEIVED");
    }

    // Package-private — shared with ShopPickupBookingController.
    static String value(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object v = body.get(key);
        if (v == null) v = body.get(key.toUpperCase());
        if (v == null) v = body.get(key.toLowerCase());
        return v == null ? null : v.toString();
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Double.parseDouble(raw.trim()); } catch (Exception e) { return null; }
    }

    // Great-circle distance in metres between two WGS-84 coordinates.
    // Uses the standard haversine formula — accurate to ~0.5% well below the
    // 50m radius threshold the shop-arrival check cares about.
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS_M = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String cleaned = raw.replace(",", "").replace("\u20B9", "").trim();
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringValueOf(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    // Marker the employee pickup-estimate flow appends to issueSummary
    // (`<complaint>\n---PICKUP_ESTIMATE_META---{json}`). We strip it before
    // putting the issue on the ticket so the JSON doesn't leak into the
    // owner's Bookings History card or any other ticket-driven UI.
    private static final String PICKUP_META_MARKER = "---PICKUP_ESTIMATE_META---";

    private static String stripPickupMeta(String issueSummary) {
        if (issueSummary == null) return null;
        int idx = issueSummary.indexOf(PICKUP_META_MARKER);
        if (idx == -1) return issueSummary.trim();
        return issueSummary.substring(0, idx).replaceAll("\\s+$", "");
    }

    /**
     * Hand a customer-placed pickup booking off to the shop's ticket pipeline.
     * On RECEIVED_AT_SHOP the device is on the shop bench; the booking now
     * needs a tickets row so the owner's Bookings History (which reads
     * /tickets) and the existing technician-assign flow (PATCH /tickets/{id})
     * can pick it up. Returns the new tickets.id, or the existing one if a
     * mint has already happened (idempotent).
     *
     * The ticket starts at IN_DIAGNOSIS — the technician hasn't been assigned
     * yet but the shop has the device, so CREATED would understate progress.
     * The CustomerOrderMirrorService bridge (Ticket → repair_booking_events)
     * keeps the customer-side timeline in sync from here on.
     */
    // Package-private — see ShopPickupBookingController which calls this on
    // the shop-staff "Mark Received" hand-off path.
    String mintTicketFromBooking(UUID bookingId, UUID shopId, String bookingNumber) {
        // Idempotency: if the booking already carries a ticket_id, return it
        // unchanged. Catches double-fire (two PATCHes racing, or a retry from
        // a flaky network).
        String existingTicketId = null;
        try {
            existingTicketId = jdbc.queryForObject(
                    "SELECT ticket_id::text FROM repair_bookings WHERE id = CAST(? AS UUID)",
                    String.class, bookingId.toString());
        } catch (Exception ignore) { /* not found returns null below */ }
        if (existingTicketId != null && !existingTicketId.isBlank()) {
            log.info("mintTicketFromBooking: booking={} already linked to ticket={}", bookingId, existingTicketId);
            return existingTicketId;
        }

        // Pull every field the ticket needs in one round-trip. Device labels
        // come from master tables so the Bookings History card can render
        // model + image without a follow-up join.
        //
        // customer_users JOIN: repair_bookings.customer_name / customer_mobile
        // are written by the shop-side booking flow only; the customer-side
        // pickup booking flow (order-service RepairBookingController.create)
        // historically stored only customer_user_id, leaving the denormalized
        // columns NULL. Without COALESCE to customer_users.full_name /
        // .mobile, the minted ticket ends up with NULL customer_name and the
        // Bookings History card renders "-" / no mobile.
        Map<String, Object> bk;
        try {
            bk = jdbc.queryForMap(
                    "SELECT rb.id, rb.booking_number, rb.shop_id, rb.customer_user_id, " +
                            "       COALESCE(NULLIF(TRIM(rb.customer_name), ''),   cu.full_name) AS customer_name, " +
                            "       COALESCE(NULLIF(TRIM(rb.customer_mobile), ''), cu.mobile)    AS customer_mobile, " +
                            "       rb.color, " +
                            "       rb.brand_id, rb.model_id, rb.ram_option_id, rb.storage_option_id, " +
                            "       rb.estimate_amount, rb.issue_summary, " +
                            "       rb.front_image_url, rb.back_image_url, rb.video_url, " +
                            "       rb.device_pin, rb.missing_damage_parts, " +
                            "       rb.estimated_ready_at, rb.estimated_delivery_at, " +
                            "       mm.name AS model_name, mm.image_url AS model_image_url, " +
                            "       mb.name AS brand_name " +
                            "FROM repair_bookings rb " +
                            "LEFT JOIN master_models mm ON mm.id = rb.model_id " +
                            "LEFT JOIN master_brands mb ON mb.id = rb.brand_id " +
                            "LEFT JOIN customer_users cu ON cu.id = rb.customer_user_id " +
                            "WHERE rb.id = CAST(? AS UUID)",
                    bookingId.toString());
        } catch (Exception e) {
            log.warn("mintTicketFromBooking: booking lookup failed for {}: {}", bookingId, e.getMessage());
            return null;
        }

        String customerUserId = stringFrom(bk, "customer_user_id");
        String customerName = stringFrom(bk, "customer_name");
        String customerPhone = stringFrom(bk, "customer_mobile");
        // tickets.customer_id is the platform customer_users.id directly — the
        // old per-shop customers row + indirect platform_user_id link is gone
        // (see TicketService.getForCustomer). The pickup booking already
        // carries customer_user_id from the customer flow, so we use it
        // verbatim; resolvePerShopCustomerId is kept only for any legacy
        // caller that still relies on the find-or-create behaviour.
        if (customerUserId == null || customerUserId.isBlank()) {
            log.warn("mintTicketFromBooking: booking {} has no customer_user_id; skipping mint", bookingId);
            return null;
        }
        String customerId = customerUserId;

        String trackingId = "CSPEN" + (System.currentTimeMillis() % 10_000_000L);
        String deviceDisplayName = buildDeviceDisplayName(
                stringFrom(bk, "brand_name"), stringFrom(bk, "model_name"));
        String repairServicesSummary = buildServicesSummary(bookingId);
        String issueDescription = stripPickupMeta(stringFrom(bk, "issue_summary"));
        // Build the Price Summary line items the shop-owner Booking Detail
        // screen renders. tickets.price_items_json is the source of truth for
        // that section; without it the screen shows "No service items
        // recorded." even though we have estimate_amount + service rows.
        Object estimateAmount = bk.get("estimate_amount");
        String priceItemsJson = buildPriceItemsJson(bookingId, estimateAmount);

        UUID ticketId = UUID.randomUUID();
        try {
            // Nullable UUID columns: only emit the cast when we have a value
            // (CAST(NULL AS UUID) is harmless, so use a uniform expression).
            jdbc.update(
                    "INSERT INTO tickets (id, shop_id, customer_id, customer_name, customer_phone, " +
                            "    tracking_id, brand_id, model_id, ram_option_id, storage_option_id, " +
                            "    color, status, estimated_price, issue_description, " +
                            "    device_display_name, device_image_url, repair_services_summary, " +
                            "    price_items_json, " +
                            "    device_security_value, estimated_ready_at, estimated_delivery_at, " +
                            "    created_at, updated_at) " +
                            "VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, " +
                            "    ?, CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), " +
                            "    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                    ticketId.toString(), shopId.toString(), customerId, customerName, customerPhone,
                    trackingId,
                    stringFrom(bk, "brand_id"),
                    stringFrom(bk, "model_id"),
                    stringFrom(bk, "ram_option_id"),
                    stringFrom(bk, "storage_option_id"),
                    stringFrom(bk, "color"),
                    "IN_DIAGNOSIS",
                    estimateAmount,
                    issueDescription,
                    deviceDisplayName,
                    stringFrom(bk, "model_image_url"),
                    repairServicesSummary,
                    priceItemsJson,
                    stringFrom(bk, "device_pin"),
                    bk.get("estimated_ready_at"),
                    bk.get("estimated_delivery_at"));
        } catch (Exception e) {
            log.error("mintTicketFromBooking: ticket INSERT failed for booking={}: {}", bookingId, e.getMessage(), e);
            return null;
        }

        // Wire the booking back to its ticket so future reads can join.
        try {
            jdbc.update(
                    "UPDATE repair_bookings SET ticket_id = CAST(? AS UUID), updated_at = now() WHERE id = CAST(? AS UUID)",
                    ticketId.toString(), bookingId.toString());
        } catch (Exception e) {
            log.warn("mintTicketFromBooking: backlink UPDATE failed for booking={}: {}", bookingId, e.getMessage());
        }

        log.info("mintTicketFromBooking: shop={} booking={} -> ticket={} tracking={}",
                shopId, bookingNumber, ticketId, trackingId);
        return ticketId.toString();
    }

    /**
     * Find-or-create the per-shop customers row that satisfies the
     * tickets.customer_id FK. Lookup by platform_user_id, then by phone, then
     * inserts a new row. Returns null if there's no usable identity at all
     * (no platform_user_id AND no phone) — caller treats that as "skip mint".
     */
    private String resolvePerShopCustomerId(String shopId, String platformUserId, String name, String phone) {
        if (shopId == null) return null;
        if (platformUserId != null && !platformUserId.isBlank()) {
            try {
                String existing = jdbc.queryForObject(
                        "SELECT id::text FROM customers WHERE shop_id = CAST(? AS UUID) AND platform_user_id = CAST(? AS UUID) LIMIT 1",
                        String.class, shopId, platformUserId);
                if (existing != null && !existing.isBlank()) return existing;
            } catch (Exception ignore) { /* fall through to phone lookup / insert */ }
        }
        if (phone != null && !phone.isBlank()) {
            try {
                String existing = jdbc.queryForObject(
                        "SELECT id::text FROM customers WHERE shop_id = CAST(? AS UUID) AND phone = ? LIMIT 1",
                        String.class, shopId, phone);
                if (existing != null && !existing.isBlank()) {
                    // Opportunistically backfill the platform link so the
                    // next lookup uses the faster index path.
                    if (platformUserId != null && !platformUserId.isBlank()) {
                        try {
                            jdbc.update(
                                    "UPDATE customers SET platform_user_id = CAST(? AS UUID), updated_at = now() " +
                                            "WHERE id = CAST(? AS UUID) AND platform_user_id IS NULL",
                                    platformUserId, existing);
                        } catch (Exception ignore) { /* best effort */ }
                    }
                    return existing;
                }
            } catch (Exception ignore) { /* fall through to insert */ }
        }
        // No existing row — create one. Customers requires (shop_id, name, phone)
        // NOT NULL; supply blanks if either is missing (better than failing
        // the entire ticket mint over a missing phone).
        if ((name == null || name.isBlank()) && (phone == null || phone.isBlank())) return null;
        String newId = UUID.randomUUID().toString();
        try {
            jdbc.update(
                    "INSERT INTO customers (id, shop_id, name, phone, platform_user_id, created_at, updated_at) " +
                            "VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, " +
                            (platformUserId != null && !platformUserId.isBlank() ? "CAST(? AS UUID)" : "NULL") +
                            ", now(), now())",
                    platformUserId != null && !platformUserId.isBlank()
                            ? new Object[]{newId, shopId, name == null ? "Customer" : name, phone == null ? "" : phone, platformUserId}
                            : new Object[]{newId, shopId, name == null ? "Customer" : name, phone == null ? "" : phone});
            return newId;
        } catch (Exception e) {
            log.warn("resolvePerShopCustomerId: insert failed shop={} phone={}: {}", shopId, phone, e.getMessage());
            return null;
        }
    }

    private static String buildDeviceDisplayName(String brand, String model) {
        if (brand == null && model == null) return null;
        if (brand == null) return model;
        if (model == null) return brand;
        return brand + " " + model;
    }

    private String buildServicesSummary(UUID bookingId) {
        try {
            List<String> names = jdbc.query(
                    "SELECT service_name FROM repair_booking_services WHERE booking_id = CAST(? AS UUID) ORDER BY created_at ASC",
                    (rs, rn) -> rs.getString("service_name"),
                    bookingId.toString());
            if (names == null || names.isEmpty()) return null;
            return String.join(", ", names);
        } catch (Exception e) {
            log.warn("buildServicesSummary: lookup failed for booking={}: {}", bookingId, e.getMessage());
            return null;
        }
    }

    /**
     * Build the JSON array the shop-owner Booking Detail "Price Summary"
     * section renders. Each entry mirrors the shape the walk-in booking
     * flow writes: {id, code, label, amount, warranty}. When the source
     * repair_booking_services rows have no per-item price (customer flow
     * just records the service name + a single bundle estimate), we fall
     * back to splitting the booking-level estimate_amount evenly across
     * the rows so the section isn't empty.
     */
    private String buildPriceItemsJson(UUID bookingId, Object bookingEstimate) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.query(
                    "SELECT id, service_code, service_name, repair_service_id, estimated_price " +
                            "FROM repair_booking_services WHERE booking_id = CAST(? AS UUID) ORDER BY created_at ASC",
                    (rs, rn) -> {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("id", rs.getString("id"));
                        r.put("code", rs.getString("service_code"));
                        r.put("label", rs.getString("service_name"));
                        r.put("repairServiceId", rs.getString("repair_service_id"));
                        BigDecimal price = rs.getBigDecimal("estimated_price");
                        if (price != null) r.put("amount", price);
                        return r;
                    },
                    bookingId.toString());
        } catch (Exception e) {
            log.warn("buildPriceItemsJson: lookup failed for booking={}: {}", bookingId, e.getMessage());
            return null;
        }
        if (rows == null || rows.isEmpty()) return null;

        BigDecimal totalFromRows = rows.stream()
                .map(r -> r.get("amount"))
                .filter(a -> a instanceof BigDecimal)
                .map(a -> (BigDecimal) a)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean anyRowPriced = totalFromRows.signum() > 0;

        // Customer-side bookings store one bundle price (estimate_amount) and
        // leave per-row prices null. Spread the bundle across rows so the
        // Price Summary table renders an amount on each line without making
        // up data — split equally when there's no per-row price at all.
        if (!anyRowPriced && bookingEstimate != null) {
            BigDecimal bundle;
            try {
                bundle = bookingEstimate instanceof BigDecimal
                        ? (BigDecimal) bookingEstimate
                        : new BigDecimal(bookingEstimate.toString());
            } catch (Exception e) { bundle = null; }
            if (bundle != null && bundle.signum() > 0 && !rows.isEmpty()) {
                BigDecimal per = bundle.divide(BigDecimal.valueOf(rows.size()), 2, java.math.RoundingMode.HALF_UP);
                for (Map<String, Object> r : rows) r.put("amount", per);
            }
        }

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows);
        } catch (Exception e) {
            log.warn("buildPriceItemsJson: serialize failed for booking={}: {}", bookingId, e.getMessage());
            return null;
        }
    }

    // Package-private — see ShopPickupBookingController.
    void appendEvent(UUID bookingId, String status, String note, String actor) {
        jdbc.update(
                "INSERT INTO repair_booking_events (id, booking_id, status, note, actor, created_at) " +
                        "VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, now())",
                UUID.randomUUID().toString(), bookingId.toString(), status, note, actor);
    }

    private void notifyCustomer(Map<String, Object> booking, UUID bookingId, String status, String title, String body) {
        String customerUserStr = stringFrom(booking, "customer_user_id");
        if (customerUserStr == null) return;
        try {
            jdbc.update(
                    "INSERT INTO customer_notifications " +
                            "(id, customer_user_id, booking_id, booking_number, status_key, title, body, type, is_read, created_at) " +
                            "VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, 'orders', false, now())",
                    UUID.randomUUID().toString(),
                    customerUserStr,
                    bookingId.toString(),
                    stringFrom(booking, "booking_number"),
                    status,
                    title,
                    body);
        } catch (Exception e) {
            log.warn("pickup-notification: insert failed for {}: {}", bookingId, e.getMessage());
        }
    }

    private Map<String, Object> loadBookingSnapshot(UUID id) {
        return jdbc.queryForMap(
                "SELECT rb.id, rb.booking_number, rb.shop_id, rb.customer_user_id, rb.status, rb.issue_summary, " +
                        "rb.color, rb.estimate_amount, rb.final_amount, rb.front_image_url, rb.back_image_url, rb.video_url, " +
                        "rb.brand_id, rb.model_id, rb.ram_option_id, rb.storage_option_id, " +
                        "rb.assigned_pickup_person_id, rb.updated_at, " +
                        "mb.name AS brand_name, " +
                        "mm.name AS model_name, " +
                        "mm.image_url AS model_image_url, " +
                        "mm.image_base64 AS model_image_base64, " +
                        "mr.label AS ram_label, " +
                        "ms.label AS storage_label " +
                        "FROM repair_bookings rb " +
                        "LEFT JOIN master_brands mb ON mb.id = rb.brand_id " +
                        "LEFT JOIN master_models mm ON mm.id = rb.model_id " +
                        "LEFT JOIN master_ram_options mr ON mr.id = rb.ram_option_id " +
                        "LEFT JOIN master_storage_options ms ON ms.id = rb.storage_option_id " +
                        "WHERE rb.id = CAST(? AS UUID)",
                id.toString());
    }

    private Map<String, Object> estimateResponse(UUID id, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        out.put("bookingNumber", stringFrom(row, "booking_number"));
        out.put("status", stringFrom(row, "status"));
        out.put("issueSummary", stringFrom(row, "issue_summary"));
        out.put("color", stringFrom(row, "color"));
        out.put("estimateAmount", stringFrom(row, "estimate_amount"));
        out.put("estimatedRepairValue", stringFrom(row, "estimate_amount"));
        out.put("frontImageUrl", stringFrom(row, "front_image_url"));
        out.put("backImageUrl", stringFrom(row, "back_image_url"));
        out.put("videoUrl", stringFrom(row, "video_url"));
        out.put("brandId", stringFrom(row, "brand_id"));
        out.put("modelId", stringFrom(row, "model_id"));
        out.put("ramOptionId", stringFrom(row, "ram_option_id"));
        out.put("storageOptionId", stringFrom(row, "storage_option_id"));
        out.put("brandName", stringFrom(row, "brand_name"));
        out.put("modelName", stringFrom(row, "model_name"));
        out.put("deviceImageUrl", stringFrom(row, "model_image_url"));
        out.put("modelImageUrl", stringFrom(row, "model_image_url"));
        out.put("deviceImageBase64", stringFrom(row, "model_image_base64"));
        out.put("modelImageBase64", stringFrom(row, "model_image_base64"));
        out.put("ramLabel", stringFrom(row, "ram_label"));
        out.put("storageLabel", stringFrom(row, "storage_label"));
        out.put("services", loadServices(id));
        out.put("events", loadEvents(id));
        return out;
    }

    private BookingAccess requireAssignedPickup(HttpServletRequest request, UUID id) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
        Technician me = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not a technician of this shop"));
        Map<String, Object> row;
        try {
            row = loadBookingSnapshot(id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "booking not found");
        }
        String assignedStr = stringFrom(row, "assigned_pickup_person_id");
        String bookingShopStr = stringFrom(row, "shop_id");
        if (assignedStr == null || !me.getId().toString().equalsIgnoreCase(assignedStr)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your pickup");
        }
        if (bookingShopStr == null || !shopId.toString().equalsIgnoreCase(bookingShopStr)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "wrong shop");
        }
        return new BookingAccess(shopId, userId, me.getId(), row);
    }

    private ResponseEntity<Map<String, Object>> error(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        return ResponseEntity.status(status != null ? status : HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getReason() == null ? "request failed" : e.getReason()));
    }

    private static class BookingAccess {
        final UUID shopId;
        final UUID userId;
        final UUID technicianId;
        final Map<String, Object> booking;

        BookingAccess(UUID shopId, UUID userId, UUID technicianId, Map<String, Object> booking) {
            this.shopId = shopId;
            this.userId = userId;
            this.technicianId = technicianId;
            this.booking = booking;
        }
    }

    private String loadAddressText(String addressId) {
        try {
            return jdbc.query(
                    "SELECT address_line, locality, city, state, pincode " +
                            "FROM customer_addresses WHERE id = CAST(? AS UUID)",
                    rs -> rs.next() ? joinAddress(
                            rs.getString("address_line"),
                            rs.getString("locality"),
                            rs.getString("city"),
                            rs.getString("state"),
                            rs.getString("pincode")
                    ) : null,
                    addressId);
        } catch (Exception e) {
            log.warn("address lookup failed for {}: {}", addressId, e.getMessage());
            return null;
        }
    }

    // Booking event log — required so the screen can bucket PICKUP_REASSIGNED
    // vs PICKUP_ASSIGNED. Wrapped in try/catch so a missing table or schema
    // drift doesn't 500 the whole endpoint.
    private List<Map<String, Object>> loadServices(UUID bookingId) {
        // Best-effort SELECT — warranty column is new (added by migration 25),
        // so older deployments may not have it yet. Try the rich query first
        // and fall back to the legacy column list on a SQL error rather than
        // killing the whole endpoint.
        try {
            return jdbc.query(
                    "SELECT id, repair_service_id, service_code, service_name, estimated_price, warranty, created_at " +
                            "FROM repair_booking_services WHERE booking_id = CAST(? AS UUID) ORDER BY created_at ASC",
                    (rs, rn) -> {
                        Map<String, Object> s = new LinkedHashMap<>();
                        String repairServiceId = rs.getString("repair_service_id");
                        BigDecimal estimatedPrice = rs.getBigDecimal("estimated_price");
                        s.put("id", rs.getString("id"));
                        s.put("serviceId", repairServiceId);
                        s.put("repairServiceId", repairServiceId);
                        s.put("serviceCode", rs.getString("service_code"));
                        s.put("code", rs.getString("service_code"));
                        s.put("serviceName", rs.getString("service_name"));
                        s.put("name", rs.getString("service_name"));
                        s.put("estimatedPrice", estimatedPrice);
                        s.put("price", estimatedPrice);
                        s.put("warranty", rs.getString("warranty"));
                        Timestamp created = rs.getTimestamp("created_at");
                        s.put("createdAt", created != null ? created.toInstant().toString() : null);
                        return s;
                    },
                    bookingId.toString());
        } catch (Exception e) {
            log.warn("services lookup failed for booking {}: {}", bookingId, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> loadEvents(UUID bookingId) {
        try {
            return jdbc.query(
                    "SELECT id, status, note, actor, latitude, longitude, distance_meters, created_at " +
                            "FROM repair_booking_events " +
                            "WHERE booking_id = CAST(? AS UUID) ORDER BY created_at ASC",
                    (rs, rn) -> {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("id", rs.getString("id"));
                        e.put("status", rs.getString("status"));
                        e.put("note", rs.getString("note"));
                        e.put("actor", rs.getString("actor"));
                        BigDecimal lat = rs.getBigDecimal("latitude");
                        BigDecimal lng = rs.getBigDecimal("longitude");
                        if (lat != null) e.put("latitude", lat);
                        if (lng != null) e.put("longitude", lng);
                        int dm = rs.getInt("distance_meters");
                        if (!rs.wasNull()) e.put("distanceMeters", dm);
                        Timestamp t = rs.getTimestamp("created_at");
                        e.put("createdAt", t != null ? t.toInstant().toString() : null);
                        return e;
                    },
                    bookingId.toString());
        } catch (Exception e) {
            log.warn("events lookup failed for booking {}: {}", bookingId, e.getMessage());
            return List.of();
        }
    }

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    // Package-private — shared with ShopPickupBookingController.
    static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String joinAddress(String line, String locality, String city, String state, String pincode) {
        List<String> parts = new ArrayList<>();
        if (line     != null && !line.isBlank())     parts.add(line.trim());
        if (locality != null && !locality.isBlank()) parts.add(locality.trim());
        if (city     != null && !city.isBlank())     parts.add(city.trim());
        if (state    != null && !state.isBlank())    parts.add(state.trim());
        if (pincode  != null && !pincode.isBlank())  parts.add(pincode.trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
