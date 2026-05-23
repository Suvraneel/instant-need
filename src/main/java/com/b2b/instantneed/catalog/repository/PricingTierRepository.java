package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.PricingTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PricingTierRepository extends JpaRepository<PricingTier, UUID> {

    List<PricingTier> findByProductIdOrderByMinQuantityAsc(UUID productId);

    List<PricingTier> findByProductIdInOrderByMinQuantityAsc(Collection<UUID> productIds);
}
