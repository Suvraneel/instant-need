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
            int page, int limit, String sort) {

        AvailabilityStatus status = parseAvailability(availability);
        Pageable pageable = buildPageable(page, limit, sort);

        Specification<Product> spec = Specification
                .where(ProductSpecification.activeOnly())
                .and(ProductSpecification.hasSearch(search))
                .and(ProductSpecification.inCategory(categoryId))
                .and(ProductSpecification.hasAvailability(status));

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
            BigDecimal startingPrice = tiers.isEmpty() ? p.getBasePrice()
                    : tiers.get(0).getUnitPrice();
            String currencyCode = tiers.isEmpty() ? null : tiers.get(0).getCurrencyCode();
            return new ProductSummaryResponse(
                    p.getId(), p.getName(), p.getSku(), p.getUnitOfMeasurement(),
                    p.getAvailabilityStatus().name(), startingPrice, currencyCode,
                    primaryImageMap.get(p.getId())
            );
        });

        return PagedResponse.of(summaryPage);
    }

    public ProductDetailResponse getProduct(UUID id) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + id));

        List<PricingTier> tiers = pricingTierRepository.findByProductIdOrderByMinQuantityAsc(id);
        List<String> imageUrls = productImageRepository
                .findByProductIdInOrderBySortOrderAsc(List.of(id))
                .stream().map(ProductImage::getImageUrl).toList();

        return new ProductDetailResponse(
                product.getId(), product.getName(), product.getSlug(), product.getSku(),
                product.getDescription(), product.getUnitOfMeasurement(),
                product.getAvailabilityStatus().name(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                imageUrls,
                tiers.stream().map(PricingTierResponse::from).toList()
        );
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
