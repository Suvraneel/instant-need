package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record AdminCustomerSummary(
        UUID customerId,
        UUID userId,
        String email,
        String phoneNumber,
        String fullName,
        String businessName,
        String role,
        boolean active,
        Instant memberSince
) {
    public static AdminCustomerSummary from(User user, Customer customer) {
        return new AdminCustomerSummary(
                customer.getId(), user.getId(),
                user.getEmail(), user.getPhoneNumber(),
                customer.getFullName(), customer.getBusinessName(),
                user.getRole().name(),
                user.isEnabled(), customer.getCreatedAt()
        );
    }
}
