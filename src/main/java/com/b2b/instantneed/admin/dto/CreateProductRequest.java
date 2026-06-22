package com.b2b.instantneed.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String slug,
        @NotBlank @Size(max = 100) String sku,
        UUID categoryId,
        String description,
        @Size(max = 50) String unitOfMeasurement,
        String availabilityStatus,
        BigDecimal basePrice,
        Integer stock,
        Integer moq,
        Boolean active,
        @Valid List<PricingTierRequest> pricingTiers,
        @Valid List<ProductImageRequest> images
) {}
