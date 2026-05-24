package com.b2b.instantneed.order.repository;

import com.b2b.instantneed.order.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    /**
     * Aggregate units sold and revenue per product in the given window.
     * Cancelled orders are excluded via the {@code excluded} parameter.
     * {@code fromIso} / {@code toIso} are ISO-8601 strings (or null) to avoid
     * PostgreSQL's "cannot determine data type of parameter $N" for typed nulls.
     * Returns rows as Object[]: [productNameSnapshot, skuSnapshot, currencyCode,
     *                            product.id (nullable), SUM(quantity), SUM(lineTotal)]
     */
    @Query(nativeQuery = true, value = """
            SELECT i.product_name_snapshot,
                   i.sku_snapshot,
                   i.currency_code,
                   CAST(i.product_id AS VARCHAR),
                   SUM(i.quantity),
                   SUM(i.line_total)
            FROM order_items i
            JOIN orders o ON o.id = i.order_id
            WHERE o.status <> :excluded
              AND (CAST(:fromIso AS TIMESTAMPTZ) IS NULL OR o.placed_at >= CAST(:fromIso AS TIMESTAMPTZ))
              AND (CAST(:toIso   AS TIMESTAMPTZ) IS NULL OR o.placed_at <= CAST(:toIso   AS TIMESTAMPTZ))
            GROUP BY i.product_id, i.product_name_snapshot, i.sku_snapshot, i.currency_code
            ORDER BY SUM(i.line_total) DESC
            """)
    List<Object[]> aggregateByProduct(
            @Param("excluded") String excluded,
            @Param("fromIso") String fromIso,
            @Param("toIso")   String toIso,
            Pageable pageable);
}
