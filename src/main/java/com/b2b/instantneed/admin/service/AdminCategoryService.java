package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminCategoryRequest;
import com.b2b.instantneed.admin.dto.AdminCategoryResponse;
import com.b2b.instantneed.catalog.entity.Category;
import com.b2b.instantneed.catalog.repository.CategoryRepository;
import com.b2b.instantneed.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc()
                .stream().map(AdminCategoryResponse::from).toList();
    }

    @Transactional
    public AdminCategoryResponse createCategory(AdminCategoryRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("NAME_REQUIRED", "Category name is required");
        }

        String slug = resolveUniqueSlug(request.slug(), request.name(), null);

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND",
                            "Parent category not found: " + request.parentId()));
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(slug)
                .parent(parent)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .active(request.active() == null || request.active())
                .build();

        return AdminCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public AdminCategoryResponse updateCategory(UUID id, AdminCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found: " + id));

        if (request.name() != null && !request.name().isBlank()) category.setName(request.name());
        if (request.slug() != null && !request.slug().isBlank()) {
            category.setSlug(resolveUniqueSlug(request.slug(), null, id));
        }
        if (request.sortOrder() != null) category.setSortOrder(request.sortOrder());
        if (request.active() != null) category.setActive(request.active());
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw ApiException.badRequest("INVALID_PARENT", "Category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND",
                            "Parent category not found: " + request.parentId()));
            category.setParent(parent);
        }

        return AdminCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found: " + id));
        category.setActive(false);
        categoryRepository.save(category);
    }

    private String resolveUniqueSlug(String requested, String name, UUID excludeId) {
        String base = (requested != null && !requested.isBlank()) ? slugify(requested) : slugify(name);
        String candidate = base;
        int counter = 2;
        while (excludeId == null
                ? categoryRepository.existsBySlug(candidate)
                : categoryRepository.existsBySlugAndIdNot(candidate, excludeId)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private static String slugify(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .strip();
    }
}
