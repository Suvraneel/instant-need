package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.catalog.dto.PricingTierResponse;
import com.b2b.instantneed.catalog.entity.*;
import com.b2b.instantneed.catalog.repository.*;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.common.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PricingTierRepository pricingTierRepository;
    private final ProductImageRepository productImageRepository;
    private final StorageService storageService;
    private final AuditLogService auditLog;
    @Transactional(readOnly = true)
    public PagedResponse<AdminProductSummary> listProducts(
            String search, UUID categoryId, Boolean active, int page, int limit) {

        int safePage = Math.max(1, page) - 1;
        int safeLimit = Math.min(Math.max(1, limit), 100);

        Specification<Product> activeSpec = active != null
                ? activeFilter(active)
                : (Specification<Product>) (r, q, cb) -> cb.conjunction();
        Specification<Product> spec = Specification
                .where(activeSpec)
                .and(com.b2b.instantneed.catalog.repository.ProductSpecification.hasSearch(search))
                .and(com.b2b.instantneed.catalog.repository.ProductSpecification.inCategory(categoryId));

        Page<Product> results = productRepository.findAll(
                spec, PageRequest.of(safePage, safeLimit, Sort.by("createdAt").descending()));

        return PagedResponse.of(results.map(p -> {
            // category is lazy — fine within @Transactional
            return AdminProductSummary.from(p);
        }));
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getProduct(UUID id) {
        Product product = findProduct(id);
        return AdminProductResponse.from(product);
    }

    @Transactional
    public AdminProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw ApiException.conflict("SKU_TAKEN", "A product with SKU '" + request.sku() + "' already exists");
        }

        String slug = resolveUniqueSlug(request.slug(), request.name(), null);

        AvailabilityStatus status = parseAvailability(request.availabilityStatus(), AvailabilityStatus.IN_STOCK);

        Category category = request.categoryId() != null
                ? categoryRepository.findById(request.categoryId())
                        .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND",
                                "Category not found: " + request.categoryId()))
                : null;

        Product product = Product.builder()
                .name(HtmlSanitizer.strip(request.name()))
                .slug(slug)
                .sku(request.sku())
                .category(category)
                .description(HtmlSanitizer.strip(request.description()))
                .unitOfMeasurement(request.unitOfMeasurement())
                .availabilityStatus(status)
                .basePrice(request.basePrice())
                .active(true)
                .build();

        product = productRepository.save(product);

        applyTiers(product, request.pricingTiers());
        applyImages(product, request.images());

        AdminProductResponse created = AdminProductResponse.from(
                productRepository.findWithCategoryById(product.getId()).orElseThrow());
        auditLog.log(AuditLogService.CREATE, AuditLogService.PRODUCT, product.getId(),
                "Created product: " + product.getName(), null, created);
        return created;
    }

    @Transactional
    public AdminProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findProduct(id);
        AdminProductResponse before = AdminProductResponse.from(product);

        if (request.sku() != null && !request.sku().equals(product.getSku())) {
            if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
                throw ApiException.conflict("SKU_TAKEN", "A product with SKU '" + request.sku() + "' already exists");
            }
            product.setSku(request.sku());
        }
        if (request.name() != null && !request.name().isBlank()) {
            product.setName(HtmlSanitizer.strip(request.name()));
        }
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = resolveUniqueSlug(request.slug(), null, id);
            product.setSlug(slug);
        }
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND",
                            "Category not found: " + request.categoryId()));
            product.setCategory(category);
        }
        if (request.description() != null) product.setDescription(HtmlSanitizer.strip(request.description()));
        if (request.unitOfMeasurement() != null) product.setUnitOfMeasurement(request.unitOfMeasurement());
        if (request.availabilityStatus() != null) {
            product.setAvailabilityStatus(parseAvailability(request.availabilityStatus(), product.getAvailabilityStatus()));
        }
        if (request.basePrice() != null) product.setBasePrice(request.basePrice());
        if (request.active() != null) product.setActive(request.active());

        productRepository.save(product);

        if (request.pricingTiers() != null) applyTiers(product, request.pricingTiers());
        if (request.images() != null) applyImages(product, request.images());

        AdminProductResponse after = AdminProductResponse.from(
                productRepository.findWithCategoryById(id).orElseThrow());
        auditLog.log(AuditLogService.UPDATE, AuditLogService.PRODUCT, id,
                "Updated product: " + product.getName(), before, after);
        return after;
    }

    // ── Pricing-tier sub-resource ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PricingTierResponse> listPricingTiers(UUID productId) {
        // Verify product exists — returns 404 if not
        findProduct(productId);
        return pricingTierRepository.findByProductIdOrderByMinQuantityAsc(productId)
                .stream().map(PricingTierResponse::from).toList();
    }

    @Transactional
    public List<PricingTierResponse> replacePricingTiers(UUID productId, List<PricingTierRequest> tierRequests) {
        Product product = findProduct(productId);
        AdminProductResponse before = AdminProductResponse.from(product);

        applyTiers(product, tierRequests);

        // Reload to return the freshly-saved state
        Product refreshed = productRepository.findWithCategoryById(productId).orElseThrow();
        List<PricingTierResponse> tiers = pricingTierRepository
                .findByProductIdOrderByMinQuantityAsc(productId)
                .stream().map(PricingTierResponse::from).toList();

        auditLog.log(AuditLogService.UPDATE, AuditLogService.PRODUCT, productId,
                "Pricing tiers replaced for product: " + product.getName(),
                before, AdminProductResponse.from(refreshed));
        return tiers;
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProduct(id);
        AdminProductResponse before = AdminProductResponse.from(product);
        product.setActive(false);
        productRepository.save(product);
        auditLog.log(AuditLogService.DELETE, AuditLogService.PRODUCT, id,
                "Soft-deleted product: " + product.getName(), before, null);
    }

    // ── Image upload ─────────────────────────────────────────────────────────────

    @Transactional
    public ImageUploadResponse uploadImage(UUID productId, MultipartFile file,
                                           String altText, int sortOrder) {
        Product product = findProduct(productId);

        String url;
        try {
            url = storageService.store(file, "products/" + productId);
        } catch (IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "Could not save file: " + e.getMessage());
        }

        // Remove any seeded placeholder images before storing a real one
        List<ProductImage> placeholders = product.getImages().stream()
                .filter(i -> i.getImageUrl() != null && i.getImageUrl().contains("placehold.co"))
                .toList();
        if (!placeholders.isEmpty()) {
            productImageRepository.deleteAll(placeholders);
            product.getImages().removeAll(placeholders);
        }

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(url)
                .altText(altText)
                .sortOrder(sortOrder)
                .build();
        productImageRepository.save(image);

        auditLog.log(AuditLogService.CREATE, AuditLogService.PRODUCT, productId,
                "Image uploaded for product: " + product.getName(), null,
                java.util.Map.of("imageUrl", url));

        return ImageUploadResponse.from(image);
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        findProduct(productId); // ensures product exists and admin has access

        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND",
                        "Image not found: " + imageId));

        if (!image.getProduct().getId().equals(productId)) {
            throw ApiException.notFound("IMAGE_NOT_FOUND", "Image not found: " + imageId);
        }

        storageService.delete(image.getImageUrl());
        productImageRepository.delete(image);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Product findProduct(UUID id) {
        return productRepository.findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + id));
    }

    private void applyTiers(Product product, List<PricingTierRequest> tierRequests) {
        if (tierRequests == null || tierRequests.isEmpty()) return;
        pricingTierRepository.deleteAll(
                pricingTierRepository.findByProductIdOrderByMinQuantityAsc(product.getId()));
        List<PricingTier> tiers = tierRequests.stream().map(t -> PricingTier.builder()
                .product(product)
                .minQuantity(t.minQuantity())
                .maxQuantity(t.maxQuantity())
                .unitPrice(t.unitPrice())
                .currencyCode(t.currencyCode() != null ? t.currencyCode() : "INR")
                .build()).toList();
        pricingTierRepository.saveAll(tiers);
        product.getPricingTiers().clear();
        product.getPricingTiers().addAll(tiers);
    }

    private void applyImages(Product product, List<ProductImageRequest> imageRequests) {
        if (imageRequests == null) return;
        productImageRepository.deleteAll(
                productImageRepository.findByProductIdInOrderBySortOrderAsc(List.of(product.getId())));
        List<ProductImage> images = imageRequests.stream().map(i -> ProductImage.builder()
                .product(product)
                .imageUrl(i.imageUrl())
                .altText(i.altText())
                .sortOrder(i.sortOrder())
                .build()).toList();
        productImageRepository.saveAll(images);
        product.getImages().clear();
        product.getImages().addAll(images);
    }

    private String resolveUniqueSlug(String requested, String name, UUID excludeId) {
        String base = (requested != null && !requested.isBlank())
                ? slugify(requested)
                : slugify(name);
        String candidate = base;
        int counter = 2;
        while (excludeId == null
                ? productRepository.existsBySlug(candidate)
                : productRepository.existsBySlugAndIdNot(candidate, excludeId)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private static String slugify(String text) {
        if (text == null || text.isBlank()) return "product";
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .strip();
    }

    private static AvailabilityStatus parseAvailability(String value, AvailabilityStatus fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return AvailabilityStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_AVAILABILITY",
                    "Must be one of: IN_STOCK, OUT_OF_STOCK, DISCONTINUED");
        }
    }

    private static Specification<Product> activeFilter(boolean active) {
        return (r, q, cb) -> cb.equal(r.get("active"), active);
    }
}
