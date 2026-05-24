package com.b2b.instantneed.catalog.dto;

import com.b2b.instantneed.catalog.entity.Product;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailResponse(
        UUID id,
        String name,
        String slug,
        String sku,
        String description,
        String unitOfMeasurement,
        String availabilityStatus,
        UUID categoryId,
        String categoryName,
        BigDecimal basePrice,
        String currencyCode,
        int stock,
        int moq,
        boolean active,
        List<ProductImageDTO> images,
        List<PricingTierResponse> pricingTiers,
        Instant createdAt,
        Instant updatedAt
) {
    public record ProductImageDTO(UUID id, String url, String altText, int displayOrder) {}

    public static ProductDetailResponse from(Product p, List<com.b2b.instantneed.catalog.entity.ProductImage> imgs, List<PricingTierResponse> tiers) {
        String currency = (tiers != null && !tiers.isEmpty()) ? tiers.get(0).currencyCode() : "INR";
        List<ProductImageDTO> imageDTOs = imgs == null ? List.of() :
                imgs.stream().map(i -> new ProductImageDTO(i.getId(), i.getImageUrl(), i.getAltText(), i.getSortOrder())).toList();
        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getSlug(), p.getSku(),
                p.getDescription(), p.getUnitOfMeasurement(),
                p.getAvailabilityStatus().name(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBasePrice(), currency,
                p.getStock(), p.getMoq(), p.isActive(),
                imageDTOs, tiers,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
