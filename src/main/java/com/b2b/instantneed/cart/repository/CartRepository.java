package com.b2b.instantneed.cart.repository;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    // Two-step: first find cart, then items are loaded via the items collection
    Optional<Cart> findByCustomerIdAndStatus(UUID customerId, CartStatus status);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.id = :cartId")
    Optional<Cart> findWithItemsById(@Param("cartId") UUID cartId);
}
