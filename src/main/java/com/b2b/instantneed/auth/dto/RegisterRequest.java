package com.b2b.instantneed.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 255) String businessName,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String phoneNumber,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 100) String gstVatNumber,
        String notes,
        @Valid AddressRequest address
) {}
