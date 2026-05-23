package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PricingTierRequest(
        @Min(1) int minQuantity,
        Integer maxQuantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
        @Size(min = 3, max = 3) String currencyCode
) {}
