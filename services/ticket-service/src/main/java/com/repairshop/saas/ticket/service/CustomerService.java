package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.CustomerRequest;
import com.repairshop.saas.ticket.dto.CustomerResponse;
import com.repairshop.saas.ticket.entity.Customer;
import com.repairshop.saas.ticket.entity.PlatformCustomerAddress;
import com.repairshop.saas.ticket.entity.PlatformCustomerUser;
import com.repairshop.saas.ticket.repository.CustomerRepository;
import com.repairshop.saas.ticket.repository.PlatformCustomerAddressRepository;
import com.repairshop.saas.ticket.repository.PlatformCustomerUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int MAX_LIST_SIZE = 200;
    private static final int MAX_PLATFORM_RESULTS = 50;

    private final CustomerRepository customerRepository;
    private final PlatformCustomerUserRepository platformCustomerUserRepository;
    private final PlatformCustomerAddressRepository platformCustomerAddressRepository;

    @Transactional
    public CustomerResponse create(UUID shopId, CustomerRequest request) {
        Customer c = Customer.builder()
                .shopId(shopId)
                .name(request.getName() != null ? request.getName().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .build();
        c = customerRepository.save(c);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(UUID shopId, String q) {
        String query = (q != null ? q.trim() : "");
        List<Customer> shopList;
        List<PlatformCustomerUser> platformList;
        if (query.isEmpty()) {
            shopList = customerRepository.findByShopIdOrderByCreatedAtDesc(
                    shopId, PageRequest.of(0, MAX_LIST_SIZE));
            platformList = Collections.emptyList();
        } else {
            shopList = customerRepository.findByShopIdAndSearch(shopId, query);
            platformList = platformCustomerUserRepository.searchActive(
                    query, PageRequest.of(0, MAX_PLATFORM_RESULTS));
        }

        Set<String> shopPhones = new HashSet<>();
        Set<UUID> linkedPlatformIds = new HashSet<>();
        List<CustomerResponse> out = new ArrayList<>(shopList.size() + platformList.size());
        for (Customer c : shopList) {
            String phone = normalizePhone(c.getPhone());
            if (phone != null) shopPhones.add(phone);
            if (c.getPlatformUserId() != null) linkedPlatformIds.add(c.getPlatformUserId());
            out.add(toResponse(c));
        }
        for (PlatformCustomerUser u : platformList) {
            if (u.getId() != null && linkedPlatformIds.contains(u.getId())) continue;
            String phone = normalizePhone(u.getMobile());
            if (phone != null && shopPhones.contains(phone)) continue;
            out.add(toPlatformResponse(u));
        }
        return out;
    }

    /**
     * Lookup a customer by exact mobile number. Checks this shop's customers
     * first; falls back to the platform customer_users table. Used by the
     * owner New-Customer form so an existing person isn't double-created.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerResponse> lookupByMobile(UUID shopId, String mobile) {
        String phone = normalizePhone(mobile);
        if (phone == null) return Optional.empty();

        List<Customer> shopMatches = customerRepository.findByShopIdAndNormalizedPhone(shopId, phone);
        if (!shopMatches.isEmpty()) {
            return Optional.of(toResponse(shopMatches.get(0)));
        }

        return platformCustomerUserRepository.findByMobile(phone)
                .map(this::toPlatformResponse);
    }

    /**
     * Materialize a per-shop customers row for a platform customer_users id.
     * Idempotent: returns the existing linked row when present.
     */
    @Transactional
    public CustomerResponse linkPlatformUser(UUID shopId, UUID platformUserId) {
        if (platformUserId == null) {
            throw new IllegalArgumentException("platformUserId is required");
        }
        return customerRepository.findByShopIdAndPlatformUserId(shopId, platformUserId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    PlatformCustomerUser u = platformCustomerUserRepository.findById(platformUserId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Platform customer not found: " + platformUserId));
                    PlatformCustomerAddress addr = platformCustomerAddressRepository
                            .findPreferred(u.getId()).orElse(null);
                    Customer c = Customer.builder()
                            .shopId(shopId)
                            .platformUserId(u.getId())
                            .name(u.getFullName() != null ? u.getFullName() : "Customer")
                            .email(u.getEmail())
                            .phone(u.getMobile() != null ? u.getMobile() : "")
                            .address(joinAddress(addr))
                            .build();
                    return toResponse(customerRepository.save(c));
                });
    }

    private static String joinAddress(PlatformCustomerAddress a) {
        if (a == null) return null;
        String joined = java.util.stream.Stream.of(
                        a.getAddressLine(), a.getLocality(), a.getCity(), a.getState(), a.getPincode())
                .filter(s -> s != null && !s.isBlank())
                .reduce((x, y) -> x + ", " + y)
                .orElse(null);
        return joined;
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s+\\-]", "");
        return s.isEmpty() ? null : s;
    }

    private CustomerResponse toResponse(Customer c) {
        // For shop rows linked to a platform user, overlay the platform's
        // structured address so the owner-side form can prefill state/city/
        // locality/pincode without the owner having to re-enter them.
        // Phone-based fallback: many shop rows were created before
        // linkPlatformUser existed (or via direct create) and have no
        // platform_user_id FK. Resolve by mobile so prefill still works.
        UUID platformId = c.getPlatformUserId();
        if (platformId == null) {
            String phone = normalizePhone(c.getPhone());
            if (phone != null) {
                platformId = platformCustomerUserRepository.findByMobile(phone)
                        .map(PlatformCustomerUser::getId)
                        .orElse(null);
            }
        }
        PlatformCustomerAddress addr = platformId != null
                ? platformCustomerAddressRepository.findPreferred(platformId).orElse(null)
                : null;
        CustomerResponse.CustomerResponseBuilder b = CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .createdAt(c.getCreatedAt())
                .source("shop")
                .platformUserId(c.getPlatformUserId());
        applyAddress(b, addr);
        return b.build();
    }

    private CustomerResponse toPlatformResponse(PlatformCustomerUser u) {
        PlatformCustomerAddress addr = platformCustomerAddressRepository
                .findPreferred(u.getId()).orElse(null);
        CustomerResponse.CustomerResponseBuilder b = CustomerResponse.builder()
                .id(u.getId())
                .name(u.getFullName())
                .email(u.getEmail())
                .phone(u.getMobile())
                .address(joinAddress(addr))
                .createdAt(null)
                .source("platform")
                .platformUserId(u.getId());
        applyAddress(b, addr);
        return b.build();
    }

    private static void applyAddress(CustomerResponse.CustomerResponseBuilder b, PlatformCustomerAddress a) {
        if (a == null) return;
        b.addressLine(a.getAddressLine())
                .locality(a.getLocality())
                .city(a.getCity())
                .state(a.getState())
                .pincode(a.getPincode());
    }
}
