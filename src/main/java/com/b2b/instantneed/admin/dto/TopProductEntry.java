package com.b2b.instantneed.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductEntry(
        UUID productId,
        String productName,
        String sku,
        long totalQuantity,
        BigDecimal totalRevenue,
        String currencyCode
) {}
