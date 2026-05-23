package com.b2b.instantneed.order.repository;

import com.b2b.instantneed.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id AND o.customer.id = :customerId")
    Optional<Order> findWithItemsByIdAndCustomerId(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(o.orderNumber, 14) AS int)), 0) FROM Order o WHERE o.orderNumber LIKE :prefix%")
    int findMaxSequenceForPrefix(@Param("prefix") String prefix);
}
