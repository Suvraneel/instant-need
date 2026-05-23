package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminOrderSummary(
        UUID orderId,
        String orderNumber,
        String status,
        String customerName,
        String paymentMethod,
        BigDecimal totalAmount,
        String currencyCode,
        int itemCount,
        Instant placedAt
) {
    public static AdminOrderSummary from(Order o) {
        return new AdminOrderSummary(
                o.getId(), o.getOrderNumber(), o.getStatus().name(),
                (String) o.getCustomerSnapshot().getOrDefault("fullName", ""),
                o.getPaymentMethod(),
                o.getTotalAmount(), o.getCurrencyCode(),
                o.getItems().size(),
                o.getPlacedAt()
        );
    }
}
