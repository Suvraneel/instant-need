package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.entity.Category;

import java.time.Instant;
import java.util.UUID;

public record AdminCategoryResponse(
        UUID id,
        String name,
        String slug,
        UUID parentId,
        String parentName,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminCategoryResponse from(Category c) {
        return new AdminCategoryResponse(
                c.getId(), c.getName(), c.getSlug(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getParent() != null ? c.getParent().getName() : null,
                c.isActive(), c.getSortOrder(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
