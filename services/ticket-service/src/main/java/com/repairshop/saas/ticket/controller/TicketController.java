package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Repair ticket CRUD")
@SecurityRequirement(name = "Bearer")
public class TicketController {

    private final TicketService ticketService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    @GetMapping("/counts")
    @Operation(summary = "Get booking/ticket counts for shop (dashboard)")
    public Map<String, Long> getCounts(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.getCountsByShop(shopId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID")
    public TicketResponse getById(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.getById(shopId, id);
    }

    @GetMapping
    @Operation(summary = "List tickets (paginated). Use assignedToMe=true for technician's assigned tickets.")
    public Page<TicketResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            Pageable pageable,
            HttpServletRequest request) {
        if (assignedToMe) {
            UUID userId = userIdFrom(request);
            if (userId == null) throw new IllegalStateException("Missing user context");
            return ticketService.listByAssignedUser(userId, pageable);
        }
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.listByShop(shopId, status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create ticket")
    public TicketResponse create(@Valid @RequestBody TicketRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.create(shopId, body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ticket")
    public TicketResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TicketRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.update(shopId, id, body);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch ticket (currently supports technician assignment and simple fields)")
    public TicketResponse patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.patch(shopId, id, body);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status")
    public void updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        ticketService.updateStatus(shopId, id, status);
    }
}
