package com.b2b.instantneed.order.dto;

import com.b2b.instantneed.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID itemId,
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasurement,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currencyCode
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductNameSnapshot(),
                item.getSkuSnapshot(),
                item.getUnitOfMeasurementSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getCurrencyCode()
        );
    }
}
