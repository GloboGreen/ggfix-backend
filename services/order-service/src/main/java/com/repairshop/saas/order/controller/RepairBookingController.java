package com.repairshop.saas.order.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.order.dto.RepairBookingDtos.*;
import com.repairshop.saas.order.entity.CustomerNotification;
import com.repairshop.saas.order.entity.CustomerOrder;
import com.repairshop.saas.order.entity.PlatformTicket;
import com.repairshop.saas.order.entity.RepairBooking;
import com.repairshop.saas.order.entity.RepairBookingEvent;
import com.repairshop.saas.order.entity.RepairBookingService;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.CustomerOrderRepository;
import com.repairshop.saas.order.repository.RepairBookingEventRepository;
import com.repairshop.saas.order.repository.RepairBookingRepository;
import com.repairshop.saas.order.repository.RepairBookingServiceRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/repair-bookings")
@RequiredArgsConstructor
public class RepairBookingController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final RepairBookingRepository bookingRepo;
    private final RepairBookingServiceRepository serviceRepo;
    private final RepairBookingEventRepository eventRepo;
    private final CustomerOrderRepository customerOrderRepo;
    private final com.repairshop.saas.order.repository.CustomerNotificationRepository notificationRepo;
    private final com.repairshop.saas.order.repository.PlatformTicketRepository platformTicketRepo;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Transactional
    public ResponseEntity<RepairBookingResponse> create(HttpServletRequest req, @RequestBody RepairBookingRequest body) {
        UUID userId = callerId(req);
        String bookingNumber = uniqueBookingNumber();
        BigDecimal estimateAmount = body.getServices() == null ? null :
                body.getServices().stream()
                        .map(ServiceRow::getEstimatedPrice)
                        .filter(p -> p != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        RepairBooking saved = bookingRepo.save(RepairBooking.builder()
                .bookingNumber(bookingNumber)
                .customerUserId(userId)
                .shopId(body.getShopId())
                .savedDeviceId(body.getSavedDeviceId())
                .brandId(body.getBrandId())
                .modelId(body.getModelId())
                .ramOptionId(body.getRamOptionId())
                .storageOptionId(body.getStorageOptionId())
                .color(body.getColor())
                .serviceMode(body.getServiceMode() != null ? body.getServiceMode() : "PICKUP")
                .frontImageUrl(body.getFrontImageUrl())
                .backImageUrl(body.getBackImageUrl())
                .videoUrl(body.getVideoUrl())
                .issueSummary(body.getIssueSummary())
                .estimateAmount(estimateAmount != null && estimateAmount.signum() > 0 ? estimateAmount : null)
                .status("ORDER_PLACED")
                .pickupAddressId(body.getPickupAddressId())
                .pickupDate(body.getPickupDate())
                .pickupSlotStart(body.getPickupSlotStart())
                .pickupSlotEnd(body.getPickupSlotEnd())
                .build());

        if (body.getServices() != null) {
            for (ServiceRow s : body.getServices()) {
                serviceRepo.save(RepairBookingService.builder()
                        .bookingId(saved.getId())
                        .repairServiceId(s.getRepairServiceId())
                        .serviceCode(s.getServiceCode())
                        .serviceName(s.getServiceName())
                        .estimatedPrice(s.getEstimatedPrice())
                        .build());
            }
        }

        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(saved.getId())
                .status("ORDER_PLACED")
                .note("Booking placed")
                .actor("SYSTEM")
                .build());

        // Write unified customer_orders row. Map service mode → orderType so
        // a doorstep-pickup repair shows in the Pickup tab of My Orders,
        // an enquiry shows in the Enquiry tab, and a walk-in stays in Service.
        final String serviceMode = saved.getServiceMode() != null ? saved.getServiceMode() : "PICKUP";
        final String orderType;
        final String title;
        switch (serviceMode) {
            case "PICKUP":
                orderType = "PICKUP";
                title = "Pickup Booking";
                break;
            case "ENQUIRY":
                orderType = "ENQUIRY";
                title = "Service Enquiry";
                break;
            default: // WALK_IN or anything else
                orderType = "REPAIR";
                title = "Service Booking";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("bookingId", saved.getId());
        payload.put("brandId", body.getBrandId());
        payload.put("modelId", body.getModelId());
        payload.put("services", body.getServices());
        payload.put("serviceMode", serviceMode);
        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { payloadJson = null; }
        customerOrderRepo.save(CustomerOrder.builder()
                .orderNumber(bookingNumber)
                .customerUserId(userId)
                .shopId(body.getShopId())
                .orderType(orderType)
                .referenceId(saved.getId())
                .status("PENDING")
                .totalAmount(estimateAmount)
                .payloadJson(payloadJson)
                .build());

        // Confirm the booking to the customer in their notification feed.
        notifyCustomer(saved, "ORDER_PLACED", "Order placed",
                "Booking " + saved.getBookingNumber() + " placed - we'll keep you posted.");

        return ResponseEntity.ok(toResponseWithChildren(saved));
    }

    @GetMapping
    public ResponseEntity<List<RepairBookingResponse>> list(HttpServletRequest req, @RequestParam(value = "status", required = false) String status) {
        UUID userId = callerId(req);
        List<RepairBooking> list = status == null || status.isBlank()
                ? bookingRepo.findByCustomerUserIdOrderByCreatedAtDesc(userId)
                : bookingRepo.findByCustomerUserIdAndStatusOrderByCreatedAtDesc(userId, status.toUpperCase());
        return ResponseEntity.ok(list.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairBookingResponse> get(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!b.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your booking");
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<RepairBookingResponse> setStatus(HttpServletRequest req, @PathVariable UUID id, @RequestParam String status) {
        UUID userId = callerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!b.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your booking");
        b.setStatus(status.toUpperCase());
        bookingRepo.save(b);
        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(b.getId()).status(status.toUpperCase()).note("Status updated").actor("USER").build());
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    // ---- Shop/owner side -------------------------------------------------

    // Bookings for the caller's shop (owner app).
    @GetMapping("/shop")
    public ResponseEntity<List<RepairBookingResponse>> listForShop(HttpServletRequest req) {
        UUID shopId = shopCallerId(req);
        List<RepairBooking> list = bookingRepo.findByShopIdOrderByCreatedAtDesc(shopId);
        return ResponseEntity.ok(list.stream().map(this::toResponseWithChildren).toList());
    }

    // Owner appends a service-timeline status (the customer History reads these
    // events). The note becomes the message shown under the step; it falls back
    // to the status key when omitted.
    @PostMapping("/{id}/shop-status")
    @Transactional
    public ResponseEntity<RepairBookingResponse> setShopStatus(HttpServletRequest req, @PathVariable UUID id, @RequestBody ShopStatusRequest body) {
        UUID shopId = shopCallerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (b.getShopId() == null || !b.getShopId().equals(shopId)) throw new ForbiddenException("Not your shop's booking");
        if (body.getStatus() == null || body.getStatus().isBlank()) throw new IllegalArgumentException("status is required");
        String status = body.getStatus().toUpperCase();
        b.setStatus(status);
        bookingRepo.save(b);
        String label = body.getNote() != null && !body.getNote().isBlank()
                ? body.getNote() : status.replace('_', ' ');
        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(b.getId()).status(status)
                .note(body.getNote() != null && !body.getNote().isBlank() ? body.getNote() : null)
                .actor("SHOP").build());
        // Drop a notification into the customer's in-app notification list so the
        // status change surfaces even when they're not on the tracking screen.
        notifyCustomer(b, status, label, "Booking " + b.getBookingNumber() + " - tap to view status");
        // Keep the unified customer_orders row in sync for terminal states.
        if (status.equals("DELIVERED") || status.equals("CANCELLED")) {
            customerOrderRepo.findByOrderNumber(b.getBookingNumber()).ifPresent(co -> {
                co.setStatus(status.equals("DELIVERED") ? "COMPLETED" : "CANCELLED");
                customerOrderRepo.save(co);
            });
        }
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    @PostMapping("/{id}/reschedule")
    @Transactional
    public ResponseEntity<RepairBookingResponse> reschedule(HttpServletRequest req, @PathVariable UUID id, @RequestBody RescheduleRequest body) {
        UUID userId = callerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!b.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your booking");
        if (body.getPickupDate() != null) b.setPickupDate(body.getPickupDate());
        if (body.getPickupSlotStart() != null) b.setPickupSlotStart(body.getPickupSlotStart());
        if (body.getPickupSlotEnd() != null) b.setPickupSlotEnd(body.getPickupSlotEnd());
        bookingRepo.save(b);
        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(b.getId()).status(b.getStatus()).note("Rescheduled").actor("USER").build());
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    // Customer marks the repair estimate as approved. Flips the customer-side
    // repair_bookings.customer_approval ("DONE") and mirrors to the owner-side
    // tickets.customer_approval (true) when the booking was shop-created.
    @PostMapping("/{id}/customer-approval")
    @Transactional
    public ResponseEntity<RepairBookingResponse> customerApproval(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!b.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your booking");
        b.setCustomerApproval("DONE");
        bookingRepo.save(b);
        if (b.getTicketId() != null) {
            platformTicketRepo.findById(b.getTicketId()).ifPresent(t -> {
                t.setCustomerApproval(Boolean.TRUE);
                platformTicketRepo.save(t);
            });
        }
        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(b.getId()).status(b.getStatus())
                .note("Customer approved repair").actor("USER").build());
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<RepairBookingResponse> cancel(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        RepairBooking b = bookingRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!b.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your booking");
        b.setStatus("CANCELLED");
        bookingRepo.save(b);
        eventRepo.save(RepairBookingEvent.builder()
                .bookingId(b.getId()).status("CANCELLED").note("Cancelled by user").actor("USER").build());
        // Also mark corresponding customer_order as CANCELLED
        customerOrderRepo.findByOrderNumber(b.getBookingNumber()).ifPresent(co -> {
            co.setStatus("CANCELLED");
            customerOrderRepo.save(co);
        });
        notifyCustomer(b, "CANCELLED", "Booking cancelled",
                "Booking " + b.getBookingNumber() + " was cancelled.");
        return ResponseEntity.ok(toResponseWithChildren(b));
    }

    // Append an in-app notification to the customer's feed for a booking event.
    private void notifyCustomer(RepairBooking b, String statusKey, String title, String body) {
        notificationRepo.save(CustomerNotification.builder()
                .customerUserId(b.getCustomerUserId())
                .bookingId(b.getId())
                .bookingNumber(b.getBookingNumber())
                .statusKey(statusKey)
                .title(title)
                .body(body)
                .type("orders")
                .read(false)
                .build());
    }

    private RepairBookingResponse toResponse(RepairBooking b) {
        // Shop-entered ticket fields (photos, security, parts, approval, schedule, estimate)
        // live on the ticket row; the booking row is populated by ticket-service's mirror
        // when the shop saves the ticket. When the mirror hasn't yet run (or ran on an older
        // code path), fall back to the linked ticket so the customer view always reflects
        // what the shop entered.
        PlatformTicket t = b.getTicketId() != null
                ? platformTicketRepo.findById(b.getTicketId()).orElse(null)
                : null;
        Map<String, String> tPhotos = t != null ? parseDevicePhotos(t.getDevicePhotosJson()) : Map.of();
        String tMissingParts = t != null ? formatMissingParts(t.getMissingPartsJson()) : null;
        String tCustomerApproval = t != null && Boolean.TRUE.equals(t.getCustomerApproval()) ? "DONE" : null;
        return RepairBookingResponse.builder()
                .id(b.getId()).bookingNumber(b.getBookingNumber())
                .shopId(b.getShopId()).ticketId(b.getTicketId()).savedDeviceId(b.getSavedDeviceId())
                .brandId(b.getBrandId()).modelId(b.getModelId())
                .ramOptionId(b.getRamOptionId()).storageOptionId(b.getStorageOptionId())
                .color(b.getColor()).serviceMode(b.getServiceMode())
                .frontImageUrl(coalesce(b.getFrontImageUrl(), tPhotos.get("front")))
                .backImageUrl(coalesce(b.getBackImageUrl(), tPhotos.get("back")))
                .videoUrl(coalesce(b.getVideoUrl(), tPhotos.get("video")))
                .issueSummary(coalesce(b.getIssueSummary(), t != null ? t.getIssueDescription() : null))
                .estimateAmount(b.getEstimateAmount() != null ? b.getEstimateAmount()
                        : (t != null ? t.getEstimatedPrice() : null))
                .finalAmount(b.getFinalAmount())
                .status(b.getStatus())
                .pickupAddressId(b.getPickupAddressId())
                .pickupDate(b.getPickupDate())
                .pickupSlotStart(b.getPickupSlotStart()).pickupSlotEnd(b.getPickupSlotEnd())
                .estimatedReadyAt(b.getEstimatedReadyAt() != null ? b.getEstimatedReadyAt()
                        : (t != null ? t.getEstimatedReadyAt() : null))
                .estimatedDurationHours(b.getEstimatedDurationHours())
                .estimatedDeliveryAt(b.getEstimatedDeliveryAt() != null ? b.getEstimatedDeliveryAt()
                        : (t != null ? t.getEstimatedDeliveryAt() : null))
                .customerApproval(coalesce(b.getCustomerApproval(), tCustomerApproval))
                .deviceSecurityType(t != null && !"NONE".equalsIgnoreCase(t.getDeviceSecurityType())
                        ? t.getDeviceSecurityType() : null)
                .devicePin(coalesce(b.getDevicePin(), t != null ? t.getDeviceSecurityValue() : null))
                .missingDamageParts(coalesce(b.getMissingDamageParts(), tMissingParts))
                .technicianName(b.getTechnicianName())
                .technicianCode(b.getTechnicianCode())
                .technicianPhotos(splitCsv(b.getTechnicianPhotos()))
                .createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt())
                .build();
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private Map<String, String> parseDevicePhotos(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> out = new HashMap<>();
            for (String k : new String[]{"front", "back", "video"}) {
                Object v = raw.get(k);
                if (v != null) out.put(k, v.toString());
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String formatMissingParts(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Object> items = objectMapper.readValue(json, new TypeReference<List<Object>>() {});
            if (items == null || items.isEmpty()) return null;
            List<String> labels = new java.util.ArrayList<>();
            for (Object it : items) {
                if (it instanceof Map<?, ?> m) {
                    Object label = m.get("label");
                    if (label == null) label = m.get("name");
                    if (label != null) labels.add(label.toString());
                } else if (it != null) {
                    labels.add(it.toString());
                }
            }
            return labels.isEmpty() ? null : String.join(", ", labels);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private RepairBookingResponse toResponseWithChildren(RepairBooking b) {
        RepairBookingResponse r = toResponse(b);
        r.setServices(serviceRepo.findByBookingId(b.getId()).stream()
                .map(s -> ServiceRow.builder()
                        .repairServiceId(s.getRepairServiceId())
                        .serviceCode(s.getServiceCode())
                        .serviceName(s.getServiceName())
                        .estimatedPrice(s.getEstimatedPrice())
                        .build()).toList());
        r.setEvents(eventRepo.findByBookingIdOrderByCreatedAtAsc(b.getId()).stream()
                .map(e -> RepairBookingEventResp.builder()
                        .id(e.getId()).status(e.getStatus()).note(e.getNote()).actor(e.getActor()).createdAt(e.getCreatedAt())
                        .build()).toList());
        return r;
    }

    private String uniqueBookingNumber() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("#CSPQX");
            for (int j = 0; j < 8; j++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String c = sb.toString();
            if (bookingRepo.findByBookingNumber(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Could not generate unique booking number");
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ForbiddenException("Missing userId");
        return UUID.fromString(u.toString());
    }

    private UUID shopCallerId(HttpServletRequest req) {
        Object s = req.getAttribute("shopId");
        if (s == null) throw new ForbiddenException("Not a shop account");
        return UUID.fromString(s.toString());
    }
}
