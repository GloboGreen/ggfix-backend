package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.CustomerRequest;
import com.repairshop.saas.ticket.dto.CustomerResponse;
import com.repairshop.saas.ticket.entity.Customer;
import com.repairshop.saas.ticket.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int MAX_LIST_SIZE = 200;

    private final CustomerRepository customerRepository;

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
        List<Customer> list;
        if (query.isEmpty()) {
            list = customerRepository.findByShopIdOrderByCreatedAtDesc(
                    shopId, PageRequest.of(0, MAX_LIST_SIZE));
        } else {
            list = customerRepository.findByShopIdAndSearch(shopId, query);
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
