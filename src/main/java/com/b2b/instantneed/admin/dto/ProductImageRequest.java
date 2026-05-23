package com.b2b.instantneed.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 255) String altText,
        int sortOrder
) {}
