package com.b2b.instantneed.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummaryResponse(
        UUID id,
        String name,
        String sku,
        String unitOfMeasurement,
        String availabilityStatus,
        BigDecimal startingPrice,
        String currencyCode,
        String imageUrl
) {}
