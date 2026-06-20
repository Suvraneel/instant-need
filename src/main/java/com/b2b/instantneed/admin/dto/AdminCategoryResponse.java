package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.entity.Category;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminCategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
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
                c.getDescription(), c.getImageUrl(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getParent() != null ? c.getParent().getName() : null,
                c.isActive(), c.getSortOrder(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
