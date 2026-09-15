package com.b2b.instantneed.order.dto;

import com.b2b.instantneed.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        String unitOfMeasurement,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        BigDecimal mrp,
        String hsnCode,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        String currencyCode,
        String imageUrl
) {
    public OrderItemResponse(
            UUID id, UUID productId, String productName, String sku, int quantity,
            BigDecimal unitPrice, BigDecimal lineTotal, String currencyCode, String imageUrl) {
        this(id, productId, productName, sku, null, quantity, unitPrice, lineTotal,
                null, null, null, null, null, null, null, currencyCode, imageUrl);
    }

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
                item.getUnitOfMeasurementSnapshot(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal(),
                item.getMrpSnapshot(),
                item.getHsnCodeSnapshot(),
                item.getCgstRate(),
                item.getSgstRate(),
                item.getTaxableAmount(),
                item.getCgstAmount(),
                item.getSgstAmount(),
                item.getCurrencyCode(),
                imageUrl
        );
    }
}
