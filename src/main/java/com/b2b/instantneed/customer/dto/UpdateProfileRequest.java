package com.b2b.instantneed.customer.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 255) String fullName,
        @Size(max = 255) String businessName,
        @Size(max = 100) String gstVatNumber,
        String notes
) {}
