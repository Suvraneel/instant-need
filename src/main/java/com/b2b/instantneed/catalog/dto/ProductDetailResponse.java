package com.b2b.instantneed.catalog.dto;

import com.b2b.instantneed.catalog.entity.Product;
import com.fasterxml.jackson.annotation.JsonInclude;

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
        List<String> imageUrls,
        List<PricingTierResponse> pricingTiers
) {
    public static ProductDetailResponse from(Product p) {
        return new ProductDetailResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getSku(),
                p.getDescription(),
                p.getUnitOfMeasurement(),
                p.getAvailabilityStatus().name(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getImages().stream().map(img -> img.getImageUrl()).toList(),
                p.getPricingTiers().stream().map(PricingTierResponse::from).toList()
        );
    }
}
