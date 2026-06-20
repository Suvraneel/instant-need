package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdminCategoryRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String slug,
        UUID parentId,
        Integer sortOrder,
        Boolean active,
        String description,
        @Size(max = 500) String imageUrl
) {}
