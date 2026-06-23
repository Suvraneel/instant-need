package com.b2b.instantneed.order.dto;

import com.b2b.instantneed.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currencyCode,
        String imageUrl
) {
    public static OrderItemResponse from(OrderItem item) {
        String imageUrl = null;
        if (item.getProduct() != null && item.getProduct().getImages() != null) {
            imageUrl = item.getProduct().getImages().stream()
                    .filter(img -> img.getImageUrl() != null && !img.getImageUrl().contains("placehold.co"))
                    .map(img -> img.getImageUrl())
                    .findFirst()
                    .orElse(null);
        }
        return new OrderItemResponse(
                item.getId(),
                item.getProduct() != null ? item.getProduct().getId() : null,
                item.getProductNameSnapshot(),
                item.getSkuSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getCurrencyCode(),
                imageUrl
        );
    }
}
