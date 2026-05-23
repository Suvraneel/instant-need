package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminProductSummary(
        UUID id,
        String name,
        String sku,
        String slug,
        String categoryName,
        String unitOfMeasurement,
        String availabilityStatus,
        boolean active,
        BigDecimal basePrice,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminProductSummary from(Product p) {
        return new AdminProductSummary(
                p.getId(), p.getName(), p.getSku(), p.getSlug(),
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getUnitOfMeasurement(),
                p.getAvailabilityStatus().name(),
                p.isActive(), p.getBasePrice(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
