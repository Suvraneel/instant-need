package com.b2b.instantneed.order.dto;

import com.b2b.instantneed.order.entity.Order;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        UUID id,
        String orderNumber,
        String status,
        String paymentMethod,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,
        String currencyCode,
        String notes,
        Instant placedAt,
        Instant updatedAt,
        AddressSnapshot shippingAddress,
        List<OrderItemResponse> items,
        String customerName,
        String customerBusinessName
) {
    public record AddressSnapshot(
            String fullName,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String phoneNumber
    ) {}

    public static OrderResponse from(Order order) {
        Map<String, Object> addrMap = order.getShippingAddressSnapshot();
        AddressSnapshot addr = addrMap == null ? null : new AddressSnapshot(
                strOf(addrMap, "fullName"),
                strOf(addrMap, "line1"),
                strOf(addrMap, "line2"),
                strOf(addrMap, "city"),
                strOf(addrMap, "state"),
                strOf(addrMap, "postalCode"),
                strOf(addrMap, "country"),
                strOf(addrMap, "phoneNumber")
        );

        Map<String, Object> custMap = order.getCustomerSnapshot();
        String customerName = custMap != null ? strOf(custMap, "fullName") : null;
        String businessName = custMap != null ? strOf(custMap, "businessName") : null;

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getPaymentMethod(),
                order.getSubtotalAmount(),
                BigDecimal.ZERO,          // discountAmount — not yet persisted
                BigDecimal.ZERO,          // shippingAmount — not yet persisted
                order.getTotalAmount(),
                order.getCurrencyCode(),
                order.getCustomerNote(),
                order.getPlacedAt(),
                order.getUpdatedAt(),
                addr,
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                customerName,
                businessName
        );
    }

    private static String strOf(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
