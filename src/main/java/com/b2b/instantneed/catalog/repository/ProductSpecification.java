package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.AvailabilityStatus;
import com.b2b.instantneed.catalog.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> hasSearch(String search) {
        if (search == null || search.isBlank()) return (root, query, cb) -> cb.conjunction();
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("sku")), pattern)
        );
    }

    public static Specification<Product> inCategory(UUID categoryId) {
        if (categoryId == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> hasAvailability(AvailabilityStatus status) {
        if (status == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.equal(root.get("availabilityStatus"), status);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        if (min == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("basePrice"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        if (max == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("basePrice"), max);
    }

    public static Specification<Product> inStockOnly(Boolean inStock) {
        if (inStock == null || !inStock) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
    }
}
