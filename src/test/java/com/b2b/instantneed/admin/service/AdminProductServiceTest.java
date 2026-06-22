package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.catalog.entity.*;
import com.b2b.instantneed.catalog.repository.*;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminProductServiceTest {

    @Mock ProductRepository      productRepository;
    @Mock CategoryRepository     categoryRepository;
    @Mock PricingTierRepository  pricingTierRepository;
    @Mock ProductImageRepository productImageRepository;
    @Mock StorageService         storageService;
    @Mock AuditLogService        auditLog;

    @InjectMocks AdminProductService service;

    // ── listProducts ──────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listProducts_returnsPagedResponse() {
        Product p = product("A4 Paper", "PAPER-A4");
        Page<Product> page = new PageImpl<>(List.of(p));
        given(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(page);

        var res = service.listProducts(null, null, null, 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).name()).isEqualTo("A4 Paper");
    }

    // ── getProduct ────────────────────────────────────────────────────────────

    @Test
    void getProduct_notFound_throws404() {
        UUID id = UUID.randomUUID();
        given(productRepository.findWithCategoryById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct(id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── createProduct ─────────────────────────────────────────────────────────

    @Test
    void createProduct_success_savesAndAudits() {
        given(productRepository.existsBySku("PAPER-A4")).willReturn(false);
        given(productRepository.existsBySlug(anyString())).willReturn(false);

        Product saved = product("A4 Paper", "PAPER-A4");
        given(productRepository.save(any())).willReturn(saved);
        given(pricingTierRepository.findByProductIdOrderByMinQuantityAsc(saved.getId()))
                .willReturn(List.of());
        given(productImageRepository.findByProductIdInOrderBySortOrderAsc(any()))
                .willReturn(List.of());
        given(productRepository.findWithCategoryById(saved.getId()))
                .willReturn(Optional.of(saved));

        AdminProductResponse res = service.createProduct(createRequest("A4 Paper", "PAPER-A4"));

        assertThat(res.name()).isEqualTo("A4 Paper");
        verify(auditLog).log(eq(AuditLogService.CREATE), eq(AuditLogService.PRODUCT), any(), any(), isNull(), any());
    }

    @Test
    void createProduct_duplicateSku_throwsConflict() {
        given(productRepository.existsBySku("TAKEN-SKU")).willReturn(true);

        assertThatThrownBy(() -> service.createProduct(createRequest("Product", "TAKEN-SKU")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createProduct_htmlInName_isSanitized() {
        given(productRepository.existsBySku("SKU")).willReturn(false);
        given(productRepository.existsBySlug(anyString())).willReturn(false);

        Product saved = product("<b>Paper</b>", "SKU");
        given(productRepository.save(any())).willReturn(saved);
        given(pricingTierRepository.findByProductIdOrderByMinQuantityAsc(saved.getId()))
                .willReturn(List.of());
        given(productImageRepository.findByProductIdInOrderBySortOrderAsc(any()))
                .willReturn(List.of());
        given(productRepository.findWithCategoryById(saved.getId()))
                .willReturn(Optional.of(saved));

        // The service strips HTML before setting name; verify via the saved entity
        service.createProduct(createRequest("<b>Paper</b>", "SKU"));

        // Capture the product that was saved and verify name was stripped
        var captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Paper");
    }

    // ── updateProduct ─────────────────────────────────────────────────────────

    @Test
    void updateProduct_changeSku_takenByOtherProduct_throwsConflict() {
        Product p = product("A4 Paper", "OLD-SKU");
        given(productRepository.findWithCategoryById(p.getId())).willReturn(Optional.of(p));
        given(productRepository.existsBySkuAndIdNot("NEW-SKU", p.getId())).willReturn(true);

        UpdateProductRequest req = new UpdateProductRequest(
                null, null, "NEW-SKU", null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateProduct(p.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateProduct_partialUpdate_onlyNonNullFieldsChanged() {
        Product p = product("Original Name", "ORIG-SKU");
        given(productRepository.findWithCategoryById(p.getId())).willReturn(Optional.of(p));
        given(productRepository.save(any())).willReturn(p);
        given(productRepository.findWithCategoryById(p.getId())).willReturn(Optional.of(p));

        UpdateProductRequest req = new UpdateProductRequest(
                "Updated Name", null, null, null, null, null, null,
                null, new BigDecimal("300.00"), null, null, null, null, null);

        service.updateProduct(p.getId(), req);

        assertThat(p.getName()).isEqualTo("Updated Name");
        assertThat(p.getSku()).isEqualTo("ORIG-SKU"); // unchanged
        verify(auditLog).log(eq(AuditLogService.UPDATE), eq(AuditLogService.PRODUCT), any(), any(), any(), any());
    }

    // ── deleteProduct ─────────────────────────────────────────────────────────

    @Test
    void deleteProduct_setsActiveToFalseAndAudits() {
        Product p = product("A4 Paper", "PAPER-A4");
        given(productRepository.findWithCategoryById(p.getId())).willReturn(Optional.of(p));

        service.deleteProduct(p.getId());

        assertThat(p.isActive()).isFalse();
        verify(productRepository).save(p);
        verify(auditLog).log(eq(AuditLogService.DELETE), eq(AuditLogService.PRODUCT), any(), any(), any(), isNull());
    }

    // ── listPricingTiers ──────────────────────────────────────────────────────

    @Test
    void listPricingTiers_productNotFound_throws404() {
        UUID id = UUID.randomUUID();
        given(productRepository.findWithCategoryById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.listPricingTiers(id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listPricingTiers_returnsTiersSortedByMinQuantity() {
        Product p = product("Paper", "P-001");
        given(productRepository.findWithCategoryById(p.getId())).willReturn(Optional.of(p));
        given(pricingTierRepository.findByProductIdOrderByMinQuantityAsc(p.getId()))
                .willReturn(List.of(
                        tier(p, 1, 49, "250.00"),
                        tier(p, 50, null, "230.00")
                ));

        var tiers = service.listPricingTiers(p.getId());

        assertThat(tiers).hasSize(2);
        assertThat(tiers.get(0).minQty()).isEqualTo(1);
        assertThat(tiers.get(1).minQty()).isEqualTo(50);
    }

    // ── slug deduplication ────────────────────────────────────────────────────

    // ── slugify / null-safety ─────────────────────────────────────────────────

    @Test
    void createProduct_nullSlugAndNullName_usesProductFallback() {
        // When both requested slug and name are blank/null, slugify returns "product"
        given(productRepository.existsBySku("SKU")).willReturn(false);
        given(productRepository.existsBySlug("product")).willReturn(false);

        Product saved = product("Unnamed", "SKU");
        given(productRepository.save(any())).willReturn(saved);
        given(pricingTierRepository.findByProductIdOrderByMinQuantityAsc(saved.getId()))
                .willReturn(List.of());
        given(productImageRepository.findByProductIdInOrderBySortOrderAsc(any()))
                .willReturn(List.of());
        given(productRepository.findWithCategoryById(saved.getId()))
                .willReturn(Optional.of(saved));

        // CreateProductRequest where name is blank triggers the fallback slug
        CreateProductRequest req = new CreateProductRequest(
                "  ", null, "SKU", null, null, "ream", "IN_STOCK",
                null, new BigDecimal("250.00"), null, null, true, List.of(), null);

        service.createProduct(req);

        var captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        // Slug derived from blank name → "product"
        assertThat(captor.getValue().getSlug()).isEqualTo("product");
    }

    @Test
    void createProduct_slugCollision_appendsCounter() {
        given(productRepository.existsBySku("SKU")).willReturn(false);
        // "paper" slug taken, "paper-2" also taken, "paper-3" is free
        given(productRepository.existsBySlug("paper")).willReturn(true);
        given(productRepository.existsBySlug("paper-2")).willReturn(true);
        given(productRepository.existsBySlug("paper-3")).willReturn(false);

        Product saved = product("Paper", "SKU");
        given(productRepository.save(any())).willReturn(saved);
        given(pricingTierRepository.findByProductIdOrderByMinQuantityAsc(saved.getId()))
                .willReturn(List.of());
        given(productImageRepository.findByProductIdInOrderBySortOrderAsc(any()))
                .willReturn(List.of());
        given(productRepository.findWithCategoryById(saved.getId()))
                .willReturn(Optional.of(saved));

        service.createProduct(createRequest("Paper", "SKU"));

        var captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("paper-3");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Product product(String name, String sku) {
        return Product.builder()
                .id(UUID.randomUUID())
                .name(name).sku(sku)
                .slug(sku.toLowerCase())
                .availabilityStatus(AvailabilityStatus.IN_STOCK)
                .basePrice(new BigDecimal("250.00"))
                .active(true)
                .build();
    }

    private PricingTier tier(Product product, int min, Integer max, String price) {
        return PricingTier.builder()
                .id(UUID.randomUUID()).product(product)
                .minQuantity(min).maxQuantity(max)
                .unitPrice(new BigDecimal(price)).currencyCode("INR").build();
    }

    private CreateProductRequest createRequest(String name, String sku) {
        return new CreateProductRequest(
                name, null, sku, null, null, "ream", "IN_STOCK",
                null, new BigDecimal("250.00"), null, null, true, List.of(), null);
    }
}
