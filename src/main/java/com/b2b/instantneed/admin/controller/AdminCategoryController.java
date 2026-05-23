package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.AdminCategoryRequest;
import com.b2b.instantneed.admin.dto.AdminCategoryResponse;
import com.b2b.instantneed.admin.service.AdminCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Categories", description = "Category management (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService service;

    @Operation(summary = "List all categories including inactive")
    @GetMapping
    public ResponseEntity<List<AdminCategoryResponse>> list() {
        return ResponseEntity.ok(service.listCategories());
    }

    @Operation(summary = "Create a category")
    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@Valid @RequestBody AdminCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request));
    }

    @Operation(summary = "Update a category (partial)")
    @PatchMapping("/{id}")
    public ResponseEntity<AdminCategoryResponse> update(
            @PathVariable UUID id, @Valid @RequestBody AdminCategoryRequest request) {
        return ResponseEntity.ok(service.updateCategory(id, request));
    }

    @Operation(summary = "Soft-delete a category (sets active=false)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
