package com.b2b.instantneed.order.dto;

import com.b2b.instantneed.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String orderNumber,
        String status,
        String paymentMethod,
        String customerNote,
        Map<String, Object> shippingAddress,
        Map<String, Object> customerSnapshot,
        List<OrderItemResponse> items,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currencyCode,
        Instant placedAt
) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getPaymentMethod(),
                order.getCustomerNote(),
                order.getShippingAddressSnapshot(),
                order.getCustomerSnapshot(),
                items,
                order.getSubtotalAmount(),
                order.getTotalAmount(),
                order.getCurrencyCode(),
                order.getPlacedAt()
        );
    }
}
