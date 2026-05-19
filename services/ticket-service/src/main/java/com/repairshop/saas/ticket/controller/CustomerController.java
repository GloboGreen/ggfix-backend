package com.repairshop.saas.ticket.controller;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new customer")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return customerService.create(shopId, body);
    }
}
