package com.b2b.instantneed.cart.dto;

import com.b2b.instantneed.cart.entity.CartItem;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID itemId,
        UUID productId,
        String productName,
        String sku,
        String slug,
        String unitOfMeasurement,
        int quantity,
        int stock,
        BigDecimal appliedUnitPrice,
        BigDecimal lineTotal,
        String currencyCode
) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSku(),
                item.getProduct().getSlug(),
                item.getProduct().getUnitOfMeasurement(),
                item.getQuantity(),
                item.getProduct().getStock(),
                item.getAppliedUnitPrice(),
                item.getLineTotal(),
                item.getCurrencyCode()
        );
    }
}
