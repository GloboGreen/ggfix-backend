package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.CustomerLinkRequest;
import com.repairshop.saas.ticket.dto.CustomerRequest;
import com.repairshop.saas.ticket.dto.CustomerResponse;
import com.repairshop.saas.ticket.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer search and create for bookings")
@SecurityRequirement(name = "Bearer")
public class CustomerController {

    private final CustomerService customerService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    @GetMapping
    @Operation(summary = "Search customers by name or phone")
    public List<CustomerResponse> list(
            @RequestParam(required = false, defaultValue = "") String q,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return customerService.search(shopId, q);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup a customer by exact mobile (this shop first, then platform). 204 if not found.")
    public ResponseEntity<CustomerResponse> lookup(
            @RequestParam("mobile") String mobile,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return customerService.lookupByMobile(shopId, mobile)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new customer")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return customerService.create(shopId, body);
    }

    @PostMapping("/link")
    @Operation(summary = "Link a platform customer_users row to this shop, creating a shop-scoped customers row if needed (idempotent)")
    public CustomerResponse link(@Valid @RequestBody CustomerLinkRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return customerService.linkPlatformUser(shopId, body.getPlatformUserId());
    }
}
