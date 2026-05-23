package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCustomerStatusRequest(
        @NotNull Boolean active
) {}
