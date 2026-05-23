package com.b2b.instantneed.order.repository;

import com.b2b.instantneed.order.entity.OrderItem;
import com.b2b.instantneed.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    /**
     * Aggregate units sold and revenue per product in the given window.
     * Cancelled orders are excluded via the {@code excluded} parameter.
     * Returns rows as Object[]: [productNameSnapshot, skuSnapshot, currencyCode,
     *                            product.id (nullable), SUM(quantity), SUM(lineTotal)]
     */
    @Query("""
            SELECT i.productNameSnapshot,
                   i.skuSnapshot,
                   i.currencyCode,
                   i.product.id,
                   SUM(i.quantity),
                   SUM(i.lineTotal)
            FROM OrderItem i
            WHERE i.order.status <> :excluded
              AND (:from IS NULL OR i.order.placedAt >= :from)
              AND (:to   IS NULL OR i.order.placedAt <= :to)
            GROUP BY i.product.id, i.productNameSnapshot, i.skuSnapshot, i.currencyCode
            ORDER BY SUM(i.lineTotal) DESC
            """)
    List<Object[]> aggregateByProduct(
            @Param("excluded") OrderStatus excluded,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
