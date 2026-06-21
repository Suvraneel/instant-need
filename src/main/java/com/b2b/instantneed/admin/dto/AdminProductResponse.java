package com.b2b.instantneed.admin.dto;

import com.b2b.instantneed.catalog.dto.PricingTierResponse;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.entity.ProductImage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProductResponse(
        UUID id,
        String name,
        String sku,
        String slug,
        UUID categoryId,
        String categoryName,
        String description,
        String unitOfMeasurement,
        String availabilityStatus,
        boolean active,
        BigDecimal basePrice,
        int stock,
        int moq,
        List<PricingTierResponse> pricingTiers,
        List<ImageInfo> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record ImageInfo(UUID id, String url, String altText, int sortOrder) {
        public static ImageInfo from(ProductImage img) {
            return new ImageInfo(img.getId(), img.getImageUrl(), img.getAltText(), img.getSortOrder());
        }
    }

    public static AdminProductResponse from(Product p) {
        return new AdminProductResponse(
                p.getId(), p.getName(), p.getSku(), p.getSlug(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getDescription(), p.getUnitOfMeasurement(),
                p.getAvailabilityStatus().name(), p.isActive(), p.getBasePrice(),
                p.getStock(), p.getMoq(),
                p.getPricingTiers().stream().map(PricingTierResponse::from).toList(),
                p.getImages().stream().map(ImageInfo::from).toList(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
