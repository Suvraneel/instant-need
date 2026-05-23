package com.b2b.instantneed.order.dto;

import java.util.UUID;

public record PlaceOrderResponse(
        UUID orderId,
        String orderNumber,
        String status,
        String message
) {}
