package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.entity.ProductImage;

import java.util.UUID;

public record ImageUploadResponse(UUID id, String url, String altText, int sortOrder) {
    public static ImageUploadResponse from(ProductImage img) {
        return new ImageUploadResponse(img.getId(), img.getImageUrl(), img.getAltText(), img.getSortOrder());
    }
}
