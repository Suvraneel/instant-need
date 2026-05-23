package com.b2b.instantneed.cart.dto;

import com.b2b.instantneed.cart.entity.Cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// cartId is null when no active cart exists yet

public record CartResponse(
        UUID cartId,
        String status,
        List<CartItemResponse> items,
        BigDecimal grandTotal,
        String currencyCode
) {
    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();

        BigDecimal grandTotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = items.isEmpty() ? null : items.get(0).currencyCode();

        return new CartResponse(
                cart.getId(),
                cart.getStatus().name(),
                items,
                grandTotal,
                currency
        );
    }

    public static CartResponse empty(UUID customerId) {
        return new CartResponse(null, "ACTIVE", List.of(), BigDecimal.ZERO, null);
    }
}
