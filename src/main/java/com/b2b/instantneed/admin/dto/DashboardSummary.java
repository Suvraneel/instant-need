package com.b2b.instantneed.admin.dto;

import java.math.BigDecimal;

public record DashboardSummary(
        long totalOrders,
        long pendingOrders,
        long processingOrders,
        long shippedOrders,
        BigDecimal revenueThisMonth,
        long totalCustomers,
        long newCustomersThisMonth,
        long activeProducts
) {}
