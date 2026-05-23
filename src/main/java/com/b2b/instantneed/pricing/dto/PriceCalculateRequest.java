package com.b2b.instantneed.pricing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PriceCalculateRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity
) {}
