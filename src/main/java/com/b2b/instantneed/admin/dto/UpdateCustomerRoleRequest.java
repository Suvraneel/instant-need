package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Promotes or demotes a customer account between CUSTOMER and ADMIN.
 * SUPER_ADMIN cannot be granted through the API — only via direct DB access.
 */
public record UpdateCustomerRoleRequest(
        @NotBlank
        @Pattern(
                regexp = "CUSTOMER|ADMIN",
                message = "role must be one of: CUSTOMER, ADMIN"
        )
        String role
) {}
