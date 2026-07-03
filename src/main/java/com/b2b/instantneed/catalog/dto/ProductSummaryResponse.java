package com.b2b.instantneed.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummaryResponse(
        UUID id,
        String name,
        String slug,
        String sku,
        String categoryName,
        BigDecimal mrp,
        BigDecimal basePrice,
        String currencyCode,
        int stock,
        int moq,
        boolean active,
        String primaryImageUrl,
        String primaryThumbnailUrl
) {}
