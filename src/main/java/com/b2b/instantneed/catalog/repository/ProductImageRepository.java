package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdInOrderBySortOrderAsc(Collection<UUID> productIds);
}
