package com.b2b.instantneed.customer.dto;

import com.b2b.instantneed.customer.entity.Address;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String fullName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String postalCode,
        String phoneNumber,
        boolean isDefault
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getFullName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getPostalCode(),
                address.getPhoneNumber(),
                address.isDefault()
        );
    }
}
