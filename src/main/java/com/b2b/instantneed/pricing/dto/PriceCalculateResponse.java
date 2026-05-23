package com.b2b.instantneed.pricing.dto;

import com.b2b.instantneed.catalog.dto.PricingTierResponse;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceCalculateResponse(
        UUID productId,
        int quantity,
        BigDecimal appliedUnitPrice,
        BigDecimal lineTotal,
        String currencyCode,
        PricingTierResponse matchedTier
) {}
