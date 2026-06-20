package com.b2b.instantneed.order.repository;

import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Page<Order> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id AND o.customer.id = :customerId")
    Optional<Order> findWithItemsByIdAndCustomerId(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(o.orderNumber, 14) AS int)), 0) FROM Order o WHERE o.orderNumber LIKE :prefix%")
    int findMaxSequenceForPrefix(@Param("prefix") String prefix);

    // ── Per-customer aggregates ───────────────────────────────────────────────

    long countByCustomerId(UUID customerId);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.customer.id = :customerId AND o.status <> :excluded
            """)
    BigDecimal sumRevenueByCustomerId(@Param("customerId") UUID customerId,
                                      @Param("excluded") OrderStatus excluded);

    // ── Reporting queries ─────────────────────────────────────────────────────

    long countByStatus(OrderStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status <> :excluded AND o.placedAt >= :from
            """)
    BigDecimal sumRevenueSince(@Param("excluded") OrderStatus excluded, @Param("from") Instant from);

    /**
     * Top customers by (non-cancelled) revenue.
     * Returns rows as Object[]: [customer.id, fullName, businessName, user.email,
     *                            COUNT(orders), SUM(totalAmount), MAX(placedAt)]
     */
    @Query("""
            SELECT o.customer.id,
                   o.customer.fullName,
                   o.customer.businessName,
                   o.customer.user.email,
                   COUNT(o),
                   SUM(o.totalAmount),
                   MAX(o.placedAt)
            FROM Order o
            WHERE o.status <> :excluded
            GROUP BY o.customer.id, o.customer.fullName, o.customer.businessName, o.customer.user.email
            ORDER BY SUM(o.totalAmount) DESC
            """)
    List<Object[]> aggregateByCustomer(@Param("excluded") OrderStatus excluded, Pageable pageable);
}
