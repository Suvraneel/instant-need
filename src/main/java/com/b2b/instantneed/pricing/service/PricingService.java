package com.b2b.instantneed.pricing.service;

import com.b2b.instantneed.catalog.dto.PricingTierResponse;
import com.b2b.instantneed.catalog.entity.PricingTier;
import com.b2b.instantneed.catalog.repository.PricingTierRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.pricing.dto.PriceCalculateRequest;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingService {

    private final ProductRepository productRepository;
    private final PricingTierRepository pricingTierRepository;

    public PriceCalculateResponse calculate(PriceCalculateRequest request) {
        return calculate(request.productId(), request.quantity());
    }

    /**
     * Core pricing calculation — server-authoritative, reused by cart and order service.
     * Finds the tier where minQuantity <= quantity <= maxQuantity (null maxQuantity = open-ended).
     */
    public PriceCalculateResponse calculate(UUID productId, int quantity) {
        var product = productRepository.findById(productId).orElseThrow(
                () -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + productId));

        List<PricingTier> tiers = pricingTierRepository
                .findByProductIdOrderByMinQuantityAsc(productId);

        // No tiers configured — fall back to the product's base price.
        if (tiers.isEmpty()) {
            BigDecimal unitPrice = product.getBasePrice() != null
                    ? product.getBasePrice()
                    : BigDecimal.ZERO;
            String currency = "INR";
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            return new PriceCalculateResponse(productId, quantity, unitPrice, lineTotal, currency, null);
        }

        // Find the matching tier; if quantity is below the first tier's minimum,
        // use the first tier's price (MOQ is informational, not enforced at order time).
        PricingTier matched = tiers.stream()
                .filter(t -> t.getMinQuantity() <= quantity
                        && (t.getMaxQuantity() == null || t.getMaxQuantity() >= quantity))
                .findFirst()
                .orElse(tiers.get(0));

        BigDecimal lineTotal = matched.getUnitPrice()
                .multiply(BigDecimal.valueOf(quantity));

        return new PriceCalculateResponse(
                productId,
                quantity,
                matched.getUnitPrice(),
                lineTotal,
                matched.getCurrencyCode(),
                PricingTierResponse.from(matched)
        );
    }
}
