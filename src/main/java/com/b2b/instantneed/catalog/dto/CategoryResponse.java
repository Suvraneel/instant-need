package com.b2b.instantneed.catalog.dto;

import com.b2b.instantneed.catalog.entity.Category;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        int sortOrder,
        List<CategoryResponse> children
) {
    public static CategoryResponse flat(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getParent() != null ? c.getParent().getId() : null,
                HtmlUtils.htmlUnescape(c.getName()),
                c.getSlug(),
                c.getDescription(),
                c.getImageUrl(),
                c.getSortOrder(),
                null
        );
    }

    public static CategoryResponse withChildren(Category c) {
        List<CategoryResponse> kids = c.getChildren().stream()
                .filter(Category::isActive)
                .map(CategoryResponse::flat)
                .toList();
        return new CategoryResponse(
                c.getId(),
                null,
                HtmlUtils.htmlUnescape(c.getName()),
                c.getSlug(),
                c.getDescription(),
                c.getImageUrl(),
                c.getSortOrder(),
                kids.isEmpty() ? null : kids
        );
    }
}
