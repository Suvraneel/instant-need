package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.controller.AdminProductController;
import com.b2b.instantneed.catalog.entity.PricingTier;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.PricingTierRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest(properties = "app.jwt.secret=aW5zdGFudC1uZWVkLXRlc3Qtb25seS1qd3Qtc2lnbmluZy1rZXktMzItYnl0ZXMtbWluaW11bQ==")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PricingTierPersistenceTest {
    @Autowired AdminProductService adminProducts;
    @Autowired ProductRepository products;
    @Autowired PricingTierRepository tiers;
    @MockitoBean AuditLogService auditLog;

    @Test
    void removingLastTierPersistsAfterReload() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Product product = products.save(Product.builder().name("Tier test")
                .slug("tier-" + suffix).sku("TIER-" + suffix)
                .basePrice(new BigDecimal("100.00")).stock(5).active(true).build());
        tiers.save(PricingTier.builder().product(product).minQuantity(2)
                .unitPrice(new BigDecimal("90.00")).currencyCode("INR").build());
        assertThat(adminProducts.listPricingTiers(product.getId())).hasSize(1);

        var mvc = MockMvcBuilders.standaloneSetup(new AdminProductController(adminProducts)).build();
        mvc.perform(patch("/api/v1/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricingTiers\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingTiers").isEmpty());

        assertThat(tiers.findByProductIdOrderByMinQuantityAsc(product.getId())).isEmpty();
        mvc.perform(get("/api/v1/admin/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingTiers").isEmpty());
    }
}
