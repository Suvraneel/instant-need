package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.admin.service.AdminProductService;
import com.b2b.instantneed.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin — Products", description = "Product catalog management (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService service;

    @Operation(summary = "List all products including inactive")
    @GetMapping
    public ResponseEntity<PagedResponse<AdminProductSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.listProducts(search, categoryId, active, page, limit));
    }

    @Operation(summary = "Get product detail with tiers and images")
    @GetMapping("/{id}")
    public ResponseEntity<AdminProductResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getProduct(id));
    }

    @Operation(summary = "Create a product with pricing tiers and images")
    @PostMapping
    public ResponseEntity<AdminProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(request));
    }

    @Operation(summary = "Update a product (partial — only non-null fields applied)")
    @PatchMapping("/{id}")
    public ResponseEntity<AdminProductResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(service.updateProduct(id, request));
    }

    @Operation(summary = "Soft-delete a product (sets active=false)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
