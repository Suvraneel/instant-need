package com.b2b.instantneed.admin.repository;

import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AdminOrderSpecification {

    private AdminOrderSpecification() {}

    public static Specification<Order> hasStatus(OrderStatus status) {
        if (status == null) return (r, q, cb) -> cb.conjunction();
        return (r, q, cb) -> cb.equal(r.get("status"), status);
    }

    public static Specification<Order> hasCustomer(UUID customerId) {
        if (customerId == null) return (r, q, cb) -> cb.conjunction();
        return (r, q, cb) -> cb.equal(r.get("customer").get("id"), customerId);
    }

    public static Specification<Order> placedAfter(Instant from) {
        if (from == null) return (r, q, cb) -> cb.conjunction();
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("placedAt"), from);
    }

    public static Specification<Order> placedBefore(Instant to) {
        if (to == null) return (r, q, cb) -> cb.conjunction();
        return (r, q, cb) -> cb.lessThanOrEqualTo(r.get("placedAt"), to);
    }

    public static Specification<Order> orderNumberContains(String search) {
        if (search == null || search.isBlank()) return (r, q, cb) -> cb.conjunction();
        return (r, q, cb) -> cb.like(cb.lower(r.get("orderNumber")), "%" + search.toLowerCase() + "%");
    }
}
