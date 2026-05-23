package com.b2b.instantneed.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull(message = "shippingAddressId is required")
        UUID shippingAddressId,

        String customerNote
) {}
