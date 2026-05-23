package com.b2b.instantneed.customer.dto;

import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        UUID customerId,
        String email,
        String phoneNumber,
        String fullName,
        String businessName,
        String gstVatNumber,
        String notes,
        UUID defaultShippingAddressId,
        Instant memberSince
) {
    public static ProfileResponse from(User user, Customer customer) {
        return new ProfileResponse(
                user.getId(),
                customer.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                customer.getFullName(),
                customer.getBusinessName(),
                customer.getGstVatNumber(),
                customer.getNotes(),
                customer.getDefaultShippingAddressId(),
                customer.getCreatedAt()
        );
    }
}
