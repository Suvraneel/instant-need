package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    long countByActiveTrue();

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") UUID id);
}
