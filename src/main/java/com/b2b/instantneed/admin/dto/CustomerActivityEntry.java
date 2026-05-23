package com.b2b.instantneed.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerActivityEntry(
        UUID customerId,
        String fullName,
        String businessName,
        String email,
        long orderCount,
        BigDecimal totalRevenue,
        Instant lastOrderAt
) {}
