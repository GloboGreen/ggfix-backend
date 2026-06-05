package com.repairshop.saas.order.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.order.dto.CustomerOrderResponse;
import com.repairshop.saas.order.entity.CustomerNotification;
import com.repairshop.saas.order.entity.CustomerOrder;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.CustomerNotificationRepository;
import com.repairshop.saas.order.repository.CustomerOrderRepository;
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
@RequestMapping("/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderRepository repo;
    private final CustomerNotificationRepository notificationRepo;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @GetMapping
    public ResponseEntity<List<CustomerOrderResponse>> list(
            HttpServletRequest req,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "status", required = false) String status
    ) {
        UUID userId = callerId(req);
        List<CustomerOrder> orders = orderType == null || orderType.isBlank()
                ? repo.findByCustomerUserIdOrderByCreatedAtDesc(userId)
                : repo.findByCustomerUserIdAndOrderTypeOrderByCreatedAtDesc(userId, orderType.toUpperCase());
        if (status != null && !status.isBlank()) {
            String upper = status.toUpperCase();
            orders = orders.stream().filter(o -> matchesStatus(upper, o.getStatus())).toList();
        }
        return ResponseEntity.ok(orders.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerOrderResponse> get(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        CustomerOrder o = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!o.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your order");
        return ResponseEntity.ok(toResponse(o));
    }

    /**
     * Checkout: turn the customer's cart (sent from the app) into a BUY order
     * so it shows in the My Orders "Buy" tab, and notify the customer. The cart
     * itself is cleared by the app via the marketplace service after this call.
     * Body: { items: [{ productId, title, price, quantity }], totalAmount }.
     */
    @PostMapping("/buy")
    @Transactional
    public ResponseEntity<CustomerOrderResponse> buy(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        UUID userId = callerId(req);
        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("items required");
        }
        BigDecimal total;
        try {
            Object t = body.get("totalAmount");
            total = t == null ? BigDecimal.ZERO : new BigDecimal(t.toString());
        } catch (NumberFormatException e) {
            total = BigDecimal.ZERO;
        }
        String title;
        if (items.size() == 1 && items.get(0) instanceof Map<?, ?> first && first.get("title") != null) {
            title = String.valueOf(first.get("title"));
        } else {
            title = items.size() == 1 ? "Item" : items.size() + " items";
        }

        String orderNumber = uniqueOrderNumber();
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("items", items);
        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { payloadJson = null; }

        CustomerOrder o = repo.save(CustomerOrder.builder()
                .orderNumber(orderNumber)
                .customerUserId(userId)
                .orderType("BUY")
                .status("PENDING")
                .totalAmount(total)
                .payloadJson(payloadJson)
                .build());

        notificationRepo.save(CustomerNotification.builder()
                .customerUserId(userId)
                .bookingNumber(orderNumber)
                .statusKey("ORDER_PLACED")
                .title("Order placed")
                .body("Your order " + orderNumber + " has been placed.")
                .type("orders")
                .read(false)
                .build());

        return ResponseEntity.ok(toResponse(o));
    }

    private String uniqueOrderNumber() {
        String n;
        do {
            StringBuilder sb = new StringBuilder("#B");
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            n = sb.toString();
        } while (repo.findByOrderNumber(n).isPresent());
        return n;
    }

    private boolean matchesStatus(String filter, String orderStatus) {
        if (orderStatus == null) return false;
        String s = orderStatus.toUpperCase();
        return switch (filter) {
            case "PENDING" -> !s.equals("COMPLETED") && !s.equals("CANCELLED");
            case "COMPLETED" -> s.equals("COMPLETED");
            case "CANCELLED" -> s.equals("CANCELLED");
            default -> s.equals(filter);
        };
    }

    private CustomerOrderResponse toResponse(CustomerOrder o) {
        Map<String, Object> payload = null;
        if (o.getPayloadJson() != null && !o.getPayloadJson().isBlank()) {
            try { payload = objectMapper.readValue(o.getPayloadJson(), new TypeReference<Map<String, Object>>() {}); }
            catch (Exception ignored) { payload = Map.of("raw", o.getPayloadJson()); }
        }
        return CustomerOrderResponse.builder()
                .id(o.getId()).orderNumber(o.getOrderNumber()).shopId(o.getShopId())
                .orderType(o.getOrderType()).referenceId(o.getReferenceId())
                .status(o.getStatus()).totalAmount(o.getTotalAmount())
                .payload(payload).createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }

    private UUID callerId(HttpServletRequest req) {
        requireRole(req, "CUSTOMER");
        Object u = req.getAttribute("userId");
        if (u == null) throw new ForbiddenException("Missing userId");
        return UUID.fromString(u.toString());
    }

    private void requireRole(HttpServletRequest req, String role) {
        Object raw = req.getAttribute("roles");
        if (raw instanceof List<?> roles && roles.stream().anyMatch(r -> role.equalsIgnoreCase(String.valueOf(r)))) {
            return;
        }
        throw new ForbiddenException("Role not allowed");
    }
}
