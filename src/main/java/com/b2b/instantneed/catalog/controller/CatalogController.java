package com.b2b.instantneed.catalog.controller;

import com.b2b.instantneed.catalog.dto.CategoryResponse;
import com.b2b.instantneed.catalog.dto.ProductDetailResponse;
import com.b2b.instantneed.catalog.dto.ProductSummaryResponse;
import com.b2b.instantneed.catalog.service.CatalogService;
import com.b2b.instantneed.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Catalog", description = "Public product catalog — no authentication required")
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "List categories (flat or tree)")
    @GetMapping("/api/v1/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories(
            @RequestParam(defaultValue = "false") boolean tree) {
        return ResponseEntity.ok(catalogService.getCategories(tree));
    }

    @Operation(summary = "List products with search, filter, and pagination")
    @GetMapping("/api/v1/products")
    public ResponseEntity<PagedResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(
                catalogService.getProducts(search, categoryId, availability,
                        minPrice, maxPrice, inStock, page, limit, sort));
    }

    @Operation(summary = "Get product detail by ID or slug")
    @GetMapping("/api/v1/products/{idOrSlug}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(catalogService.getProduct(idOrSlug));
    }

}
