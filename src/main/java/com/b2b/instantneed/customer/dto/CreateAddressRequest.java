package com.b2b.instantneed.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAddressRequest(
        @Size(max = 100) String label,
        @Size(max = 255) String fullName,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 20) String postalCode,
        @Size(max = 30) String phoneNumber,
        boolean isDefault
) {}
