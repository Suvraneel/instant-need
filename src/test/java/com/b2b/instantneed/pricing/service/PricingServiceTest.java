package com.b2b.instantneed.pricing.service;

import com.b2b.instantneed.catalog.entity.PricingTier;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.PricingTierRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock ProductRepository     productRepository;
    @Mock PricingTierRepository tierRepository;

    @InjectMocks PricingService pricingService;

    private final UUID productId = UUID.randomUUID();

    @Test
    void calculate_matchesExactTier() {
        given(productRepository.existsById(productId)).willReturn(true);
        given(tierRepository.findByProductIdOrderByMinQuantityAsc(productId))
                .willReturn(List.of(
                        tier(1, 49,  "250.00"),
                        tier(50, 199, "230.00"),
                        tier(200, null, "210.00")
                ));

        PriceCalculateResponse res = pricingService.calculate(productId, 50);

        assertThat(res.appliedUnitPrice()).isEqualByComparingTo("230.00");
        assertThat(res.lineTotal()).isEqualByComparingTo("11500.00"); // 50 * 230
        assertThat(res.quantity()).isEqualTo(50);
        assertThat(res.currencyCode()).isEqualTo("INR");
    }

    @Test
    void calculate_openEndedLastTier_matchesBulk() {
        given(productRepository.existsById(productId)).willReturn(true);
        given(tierRepository.findByProductIdOrderByMinQuantityAsc(productId))
                .willReturn(List.of(tier(1, 99, "300.00"), tier(100, null, "250.00")));

        PriceCalculateResponse res = pricingService.calculate(productId, 500);

        assertThat(res.appliedUnitPrice()).isEqualByComparingTo("250.00");
        assertThat(res.lineTotal()).isEqualByComparingTo("125000.00");
    }

    @Test
    void calculate_exactBoundary_usesCorrectTier() {
        given(productRepository.existsById(productId)).willReturn(true);
        given(tierRepository.findByProductIdOrderByMinQuantityAsc(productId))
                .willReturn(List.of(tier(1, 49, "300.00"), tier(50, null, "250.00")));

        // Exactly at boundary — should use second tier
        assertThat(pricingService.calculate(productId, 49).appliedUnitPrice())
                .isEqualByComparingTo("300.00");
        assertThat(pricingService.calculate(productId, 50).appliedUnitPrice())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void calculate_productNotFound_throwsNotFound() {
        given(productRepository.existsById(productId)).willReturn(false);

        assertThatThrownBy(() -> pricingService.calculate(productId, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void calculate_noTiersConfigured_throwsBadRequest() {
        given(productRepository.existsById(productId)).willReturn(true);
        given(tierRepository.findByProductIdOrderByMinQuantityAsc(productId))
                .willReturn(List.of());

        assertThatThrownBy(() -> pricingService.calculate(productId, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no pricing tiers");
    }

    @Test
    void calculate_quantityBelowMinimum_throwsBadRequest() {
        given(productRepository.existsById(productId)).willReturn(true);
        given(tierRepository.findByProductIdOrderByMinQuantityAsc(productId))
                .willReturn(List.of(tier(10, null, "200.00"))); // min = 10

        assertThatThrownBy(() -> pricingService.calculate(productId, 5))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No pricing tier covers quantity 5");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PricingTier tier(int min, Integer max, String price) {
        return PricingTier.builder()
                .id(UUID.randomUUID())
                .minQuantity(min)
                .maxQuantity(max)
                .unitPrice(new BigDecimal(price))
                .currencyCode("INR")
                .build();
    }
}
