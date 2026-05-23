package com.b2b.instantneed.catalog.dto;

import com.b2b.instantneed.catalog.entity.PricingTier;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PricingTierResponse(
        int minQuantity,
        Integer maxQuantity,
        BigDecimal unitPrice,
        String currencyCode
) {
    public static PricingTierResponse from(PricingTier t) {
        return new PricingTierResponse(t.getMinQuantity(), t.getMaxQuantity(), t.getUnitPrice(), t.getCurrencyCode());
    }
}
