package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminOrderSummary;
import com.b2b.instantneed.admin.dto.UpdateOrderStatusRequest;
import com.b2b.instantneed.admin.repository.AdminOrderSpecification;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.common.service.ExpoPushNotificationService;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final AuditLogService auditLog;
    private final EmailService emailService;
    private final ExpoPushNotificationService pushService;

    @Transactional(readOnly = true)
    public PagedResponse<AdminOrderSummary> listOrders(
            String status, UUID customerId, String dateFrom, String dateTo,
            String orderNumber, int page, int limit) {

        int safePage = Math.max(1, page) - 1;
        int safeLimit = Math.min(Math.max(1, limit), 100);

        OrderStatus parsedStatus = parseStatus(status);
        Instant from = parseDate(dateFrom, false);
        Instant to = parseDate(dateTo, true);

        Specification<Order> spec = Specification
                .where(AdminOrderSpecification.hasStatus(parsedStatus))
                .and(AdminOrderSpecification.hasCustomer(customerId))
                .and(AdminOrderSpecification.placedAfter(from))
                .and(AdminOrderSpecification.placedBefore(to))
                .and(AdminOrderSpecification.orderNumberContains(orderNumber));

        Page<Order> orders = orderRepository.findAll(
                spec, PageRequest.of(safePage, safeLimit, Sort.by("placedAt").descending()));

        return PagedResponse.of(orders.map(AdminOrderSummary::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found: " + orderId));

        if (request.status() == null || request.status().isBlank()) {
            throw ApiException.badRequest("MISSING_STATUS", "Status field is required");
        }
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS",
                    "Invalid status. Must be one of: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED");
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        orderRepository.save(order);
        OrderResponse response = OrderResponse.from(order);
        auditLog.log(AuditLogService.UPDATE, AuditLogService.ORDER, orderId,
                "Status changed from " + oldStatus + " to " + newStatus + " on order " + order.getOrderNumber(),
                java.util.Map.of("status", oldStatus.name()),
                java.util.Map.of("status", newStatus.name()));

        // Notify customer asynchronously
        Customer customer = order.getCustomer();
        if (customer != null && customer.getUser() != null
                && customer.getUser().getEmail() != null) {
            emailService.sendOrderStatusUpdate(customer.getUser().getEmail(), response);
        }
        if (customer != null && customer.getPushToken() != null) {
            pushService.sendOrderStatusUpdate(customer.getPushToken(), response);
        }

        return response;
    }

    private OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OrderStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS",
                    "Invalid status filter. Must be one of: PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED");
        }
    }

    private Instant parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value);
            return endOfDay
                    ? date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_DATE", "Date must be in ISO format: YYYY-MM-DD");
        }
    }
}
