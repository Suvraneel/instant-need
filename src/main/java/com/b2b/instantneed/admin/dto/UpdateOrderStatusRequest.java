package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull String status,
        String ewayBillNumber,
        String transport,
        String vehicleNumber
) {
    public UpdateOrderStatusRequest(String status) {
        this(status, null, null, null);
    }
}
