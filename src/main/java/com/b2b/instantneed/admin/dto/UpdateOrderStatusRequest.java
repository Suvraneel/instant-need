package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull String status
) {}
