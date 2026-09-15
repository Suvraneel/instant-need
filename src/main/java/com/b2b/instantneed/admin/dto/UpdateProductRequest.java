package com.b2b.instantneed.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String slug,
        @Size(max = 100) String sku,
        UUID categoryId,
        String description,
        @Size(max = 50) String unitOfMeasurement,
        String availabilityStatus,
        BigDecimal mrp,
        BigDecimal basePrice,
        @Size(max = 20) String hsnCode,
        BigDecimal cgstRate,
        BigDecimal sgstRate,
        @Min(0) Integer stock,
        @Min(1) Integer moq,
        Boolean active,
        @Valid List<PricingTierRequest> pricingTiers,
        @Valid List<ProductImageRequest> images
) {
    public UpdateProductRequest(
            String name, String slug, String sku, UUID categoryId, String description,
            String unitOfMeasurement, String availabilityStatus, BigDecimal mrp,
            BigDecimal basePrice, Integer stock, Integer moq, Boolean active,
            List<PricingTierRequest> pricingTiers, List<ProductImageRequest> images) {
        this(name, slug, sku, categoryId, description, unitOfMeasurement, availabilityStatus,
                mrp, basePrice, null, null, null, stock, moq, active, pricingTiers, images);
    }
}
