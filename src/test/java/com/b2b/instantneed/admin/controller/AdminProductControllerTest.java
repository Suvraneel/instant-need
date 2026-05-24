package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.admin.service.AdminProductService;
import com.b2b.instantneed.catalog.dto.PricingTierResponse;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.config.SecurityConfig;
import com.b2b.instantneed.common.security.JwtAuthFilter;
import com.b2b.instantneed.common.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.test.autoconfigure.webmvc.SecurityMockMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AdminProductController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                SecurityMockMvcAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthFilter.class, RateLimitFilter.class}
        )
)
class AdminProductControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminProductService service;

    // ── GET /admin/products ───────────────────────────────────────────────────

    @Test
    void listProducts_returns200WithPage() throws Exception {
        PagedResponse<AdminProductSummary> page = new PagedResponse<>(
                List.of(productSummary()), 1, 20, 1L);
        given(service.listProducts(any(), any(), any(), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("A4 Paper"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listProducts_withSearchFilter_passesParam() throws Exception {
        given(service.listProducts(eq("paper"), any(), any(), anyInt(), anyInt()))
                .willReturn(new PagedResponse<>(List.of(), 1, 20, 0L));

        mockMvc.perform(get("/api/v1/admin/products").param("search", "paper"))
                .andExpect(status().isOk());

        verify(service).listProducts(eq("paper"), any(), any(), anyInt(), anyInt());
    }

    // ── GET /admin/products/{id} ──────────────────────────────────────────────

    @Test
    void getProduct_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.getProduct(id)).willReturn(productDetail(id));

        mockMvc.perform(get("/api/v1/admin/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PAPER-A4"))
                .andExpect(jsonPath("$.pricingTiers").isArray());
    }

    // ── POST /admin/products ──────────────────────────────────────────────────

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        UUID newId = UUID.randomUUID();
        given(service.createProduct(any())).willReturn(productDetail(newId));

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("PAPER-A4"));
    }

    @Test
    void createProduct_missingName_returns400() throws Exception {
        String body = """
                {
                  "sku": "PAPER-A4",
                  "pricingTiers": [{"minQuantity": 1, "unitPrice": 250.00, "currencyCode": "INR"}]
                }
                """;
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_missingSku_returns400() throws Exception {
        String body = """
                {
                  "name": "A4 Paper",
                  "pricingTiers": [{"minQuantity": 1, "unitPrice": 250.00, "currencyCode": "INR"}]
                }
                """;
        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /admin/products/{id} ────────────────────────────────────────────

    @Test
    void updateProduct_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.updateProduct(eq(id), any())).willReturn(productDetail(id));

        mockMvc.perform(patch("/api/v1/admin/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "basePrice": 275.00 }
                                """))
                .andExpect(status().isOk());
    }

    // ── DELETE /admin/products/{id} ───────────────────────────────────────────

    @Test
    void deleteProduct_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        willDoNothing().given(service).deleteProduct(id);

        mockMvc.perform(delete("/api/v1/admin/products/{id}", id))
                .andExpect(status().isNoContent());
    }

    // ── GET /admin/products/{id}/pricing-tiers ────────────────────────────────

    @Test
    void listPricingTiers_returns200WithTiers() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.listPricingTiers(id)).willReturn(List.of(
                new PricingTierResponse(null, 1, 49,   new BigDecimal("250.00"), "INR"),
                new PricingTierResponse(null, 50, null, new BigDecimal("230.00"), "INR")
        ));

        mockMvc.perform(get("/api/v1/admin/products/{id}/pricing-tiers", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].minQty").value(1))
                .andExpect(jsonPath("$[1].minQty").value(50));
    }

    // ── PUT /admin/products/{id}/pricing-tiers ────────────────────────────────

    @Test
    void replacePricingTiers_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.replacePricingTiers(eq(id), any())).willReturn(List.of(
                new PricingTierResponse(null, 1, null, new BigDecimal("200.00"), "INR")
        ));

        mockMvc.perform(put("/api/v1/admin/products/{id}/pricing-tiers", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"minQuantity": 1, "unitPrice": 200.00, "currencyCode": "INR"}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitPrice").value(200.0));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AdminProductSummary productSummary() {
        return new AdminProductSummary(
                UUID.randomUUID(), "A4 Paper", "PAPER-A4", "a4-paper",
                null, null, "IN_STOCK", true, new BigDecimal("250.00"),
                0, 0, Instant.now(), Instant.now());
    }

    private AdminProductResponse productDetail(UUID id) {
        return new AdminProductResponse(
                id, "A4 Paper", "PAPER-A4", "a4-paper",
                null, null, "80 GSM A4 paper", "ream",
                "IN_STOCK", true, new BigDecimal("250.00"),
                List.of(
                        new PricingTierResponse(null, 1, 49, new BigDecimal("250.00"), "INR"),
                        new PricingTierResponse(null, 50, null, new BigDecimal("230.00"), "INR")
                ),
                List.of(new AdminProductResponse.ImageInfo(UUID.randomUUID(), "https://example.com/img.jpg", null, 0)),
                Instant.now(), Instant.now());
    }

    private String validCreateRequest() {
        return """
                {
                  "name": "A4 Paper",
                  "sku": "PAPER-A4",
                  "unitOfMeasurement": "ream",
                  "basePrice": 250.00,
                  "pricingTiers": [
                    { "minQuantity": 1, "unitPrice": 250.00, "currencyCode": "INR" }
                  ]
                }
                """;
    }
}
