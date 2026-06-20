package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.user.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCustomerDetail(
        UUID id,
        UUID userId,
        String email,
        String phoneNumber,
        String fullName,
        String businessName,
        String role,
        boolean active,
        Instant createdAt,
        int orderCount,
        BigDecimal totalRevenue,
        String currencyCode,
        List<OrderSummary> recentOrders
) {
    public record OrderSummary(
            UUID id,
            String orderNumber,
            String status,
            BigDecimal totalAmount,
            String currencyCode,
            Instant placedAt
    ) {
        public static OrderSummary from(Order o) {
            return new OrderSummary(
                    o.getId(), o.getOrderNumber(), o.getStatus().name(),
                    o.getTotalAmount(), o.getCurrencyCode(), o.getPlacedAt()
            );
        }
    }

    public static AdminCustomerDetail from(User user, Customer customer,
                                           List<Order> recentOrders,
                                           long orderCount, BigDecimal totalRevenue) {
        return new AdminCustomerDetail(
                customer.getId(), user.getId(),
                user.getEmail(), user.getPhoneNumber(),
                customer.getFullName(), customer.getBusinessName(),
                user.getRole().name(),
                user.isEnabled(), customer.getCreatedAt(),
                (int) orderCount, totalRevenue,
                "INR",
                recentOrders.stream().map(OrderSummary::from).toList()
        );
    }
}
