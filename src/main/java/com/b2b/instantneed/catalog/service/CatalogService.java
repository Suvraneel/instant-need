package com.b2b.instantneed.catalog.service;

import com.b2b.instantneed.catalog.dto.CategoryResponse;
import com.b2b.instantneed.catalog.dto.PricingTierResponse;
import com.b2b.instantneed.catalog.dto.ProductDetailResponse;
import com.b2b.instantneed.catalog.dto.ProductSummaryResponse;
import com.b2b.instantneed.catalog.entity.AvailabilityStatus;
import com.b2b.instantneed.catalog.entity.PricingTier;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.entity.ProductImage;
import com.b2b.instantneed.catalog.repository.*;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final PricingTierRepository pricingTierRepository;

    public List<CategoryResponse> getCategories(boolean tree) {
        if (tree) {
            return categoryRepository.findRootCategoriesWithChildren()
                    .stream()
                    .map(CategoryResponse::withChildren)
                    .toList();
        }
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(CategoryResponse::flat)
                .toList();
    }

    public PagedResponse<ProductSummaryResponse> getProducts(
            String search, UUID categoryId, String availability,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock,
            int page, int limit, String sort) {

        AvailabilityStatus status = (inStock != null && inStock)
                ? AvailabilityStatus.IN_STOCK
                : parseAvailability(availability);
        Pageable pageable = buildPageable(page, limit, sort);

        Specification<Product> spec = Specification
                .where(ProductSpecification.activeOnly())
                .and(ProductSpecification.hasSearch(search))
                .and(ProductSpecification.inCategory(categoryId))
                .and(ProductSpecification.hasAvailability(status))
                .and(ProductSpecification.priceAtLeast(minPrice))
                .and(ProductSpecification.priceAtMost(maxPrice));

        Page<Product> productPage = productRepository.findAll(spec, pageable);

        // Batch-load tiers and images to avoid N+1
        List<UUID> ids = productPage.getContent().stream().map(Product::getId).toList();
        Map<UUID, List<PricingTier>> tiersMap = pricingTierRepository
                .findByProductIdInOrderByMinQuantityAsc(ids)
                .stream().collect(Collectors.groupingBy(t -> t.getProduct().getId()));

        Map<UUID, String> primaryImageMap = productImageRepository
                .findByProductIdInOrderBySortOrderAsc(ids)
                .stream().collect(Collectors.toMap(
                        img -> img.getProduct().getId(),
                        ProductImage::getImageUrl,
                        (a, b) -> a  // keep first
                ));

        Page<ProductSummaryResponse> summaryPage = productPage.map(p -> {
            List<PricingTier> tiers = tiersMap.getOrDefault(p.getId(), List.of());
            return new ProductSummaryResponse(
                p.getId(), HtmlUtils.htmlUnescape(p.getName()), p.getSlug(), p.getSku(),
                p.getCategory() != null ? HtmlUtils.htmlUnescape(p.getCategory().getName()) : null,
                p.getBasePrice(),
                tiers.isEmpty() ? "INR" : tiers.get(0).getCurrencyCode(),
                p.getStock(), p.getMoq(), p.isActive(),
                primaryImageMap.get(p.getId())
            );
        });

        return PagedResponse.of(summaryPage);
    }

    public ProductDetailResponse getProduct(String idOrSlug) {
        Product product;
        try {
            UUID id = UUID.fromString(idOrSlug);
            product = productRepository.findWithCategoryById(id)
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + idOrSlug));
        } catch (IllegalArgumentException e) {
            // Not a UUID — try slug
            product = productRepository.findBySlug(idOrSlug)
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + idOrSlug));
            // Ensure category is loaded
            if (product.getCategory() == null && product.getId() != null) {
                product = productRepository.findWithCategoryById(product.getId()).orElse(product);
            }
        }
        UUID productId = product.getId();
        List<PricingTier> tiers = pricingTierRepository.findByProductIdOrderByMinQuantityAsc(productId);
        List<com.b2b.instantneed.catalog.entity.ProductImage> images =
                productImageRepository.findByProductIdInOrderBySortOrderAsc(List.of(productId));
        return ProductDetailResponse.from(product, images, tiers.stream().map(PricingTierResponse::from).toList());
    }

    private AvailabilityStatus parseAvailability(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AvailabilityStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_AVAILABILITY",
                    "Invalid availability value. Must be one of: IN_STOCK, OUT_OF_STOCK, DISCONTINUED");
        }
    }

    private Pageable buildPageable(int page, int limit, String sort) {
        int safePage = Math.max(1, page) - 1;   // convert 1-based to 0-based
        int safeLimit = Math.min(Math.max(1, limit), 100);
        Sort sortOrder = switch (sort == null ? "" : sort.toLowerCase()) {
            case "price_asc"  -> Sort.by("basePrice").ascending();
            case "price_desc" -> Sort.by("basePrice").descending();
            case "name_asc"   -> Sort.by("name").ascending();
            case "name_desc"  -> Sort.by("name").descending();
            default           -> Sort.by("createdAt").descending();
        };
        return PageRequest.of(safePage, safeLimit, sortOrder);
    }
}
