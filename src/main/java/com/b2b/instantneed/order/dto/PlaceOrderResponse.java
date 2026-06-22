package com.b2b.instantneed.order.dto;

import java.util.UUID;

public record PlaceOrderResponse(
        UUID id,
        String orderNumber,
        String status,
        String message
) {}
