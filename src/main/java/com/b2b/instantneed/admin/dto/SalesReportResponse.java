package com.b2b.instantneed.admin.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record SalesReportResponse(
        String dateFrom,
        String dateTo,
        long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue,
        List<DailyEntry> breakdown
) {
    public record DailyEntry(String date, long orders, BigDecimal revenue) {}

    public static BigDecimal avg(BigDecimal revenue, long orders) {
        if (orders == 0) return BigDecimal.ZERO;
        return revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }
}
