package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminCategoryRequest;
import com.b2b.instantneed.admin.dto.AdminCategoryResponse;
import com.b2b.instantneed.catalog.entity.Category;
import com.b2b.instantneed.catalog.repository.CategoryRepository;
import com.b2b.instantneed.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock AuditLogService    auditLog;

    @InjectMocks AdminCategoryService service;

    // ── listCategories ────────────────────────────────────────────────────────

    @Test
    void listCategories_returnsSortedList() {
        given(categoryRepository.findAllByOrderBySortOrderAsc())
                .willReturn(List.of(
                        category("Office Supplies", "office-supplies"),
                        category("Cleaning Products", "cleaning-products")
                ));

        List<AdminCategoryResponse> result = service.listCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Office Supplies");
    }

    // ── createCategory ────────────────────────────────────────────────────────

    @Test
    void createCategory_success_savesAndAudits() {
        given(categoryRepository.existsBySlug("office-supplies")).willReturn(false);
        Category saved = category("Office Supplies", "office-supplies");
        given(categoryRepository.save(any())).willReturn(saved);

        AdminCategoryResponse res = service.createCategory(
                new AdminCategoryRequest("Office Supplies", null, null, 1, true));

        assertThat(res.name()).isEqualTo("Office Supplies");
        assertThat(res.slug()).isEqualTo("office-supplies");
        verify(auditLog).log(eq(AuditLogService.CREATE), eq(AuditLogService.CATEGORY),
                any(), any(), isNull(), any());
    }

    @Test
    void createCategory_slugCollision_appendsCounter() {
        given(categoryRepository.existsBySlug("paper")).willReturn(true);
        given(categoryRepository.existsBySlug("paper-2")).willReturn(false);

        Category saved = category("Paper", "paper-2");
        given(categoryRepository.save(any())).willReturn(saved);

        service.createCategory(new AdminCategoryRequest("Paper", null, null, 0, true));

        var captor = org.mockito.ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("paper-2");
    }

    @Test
    void createCategory_nullName_throwsBadRequest() {
        assertThatThrownBy(() -> service.createCategory(
                new AdminCategoryRequest(null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── updateCategory ────────────────────────────────────────────────────────

    @Test
    void updateCategory_updatesNameAndSlug() {
        Category cat = category("Old Name", "old-slug");
        given(categoryRepository.findById(cat.getId())).willReturn(Optional.of(cat));
        given(categoryRepository.existsBySlugAndIdNot("new-slug", cat.getId())).willReturn(false);
        given(categoryRepository.save(any())).willReturn(cat);

        AdminCategoryResponse res = service.updateCategory(cat.getId(),
                new AdminCategoryRequest("New Name", "new-slug", null, null, null));

        assertThat(cat.getName()).isEqualTo("New Name");
        assertThat(cat.getSlug()).isEqualTo("new-slug");
        verify(auditLog).log(eq(AuditLogService.UPDATE), eq(AuditLogService.CATEGORY),
                any(), any(), any(), any());
    }

    @Test
    void updateCategory_selfParent_throwsBadRequest() {
        Category cat = category("Category", "cat");
        given(categoryRepository.findById(cat.getId())).willReturn(Optional.of(cat));

        assertThatThrownBy(() -> service.updateCategory(cat.getId(),
                new AdminCategoryRequest(null, null, cat.getId(), null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void updateCategory_notFound_throws404() {
        UUID id = UUID.randomUUID();
        given(categoryRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(id,
                new AdminCategoryRequest("X", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── deleteCategory ────────────────────────────────────────────────────────

    @Test
    void deleteCategory_setsActiveToFalseAndAudits() {
        Category cat = category("Category", "cat");
        given(categoryRepository.findById(cat.getId())).willReturn(Optional.of(cat));

        service.deleteCategory(cat.getId());

        assertThat(cat.isActive()).isFalse();
        verify(categoryRepository).save(cat);
        verify(auditLog).log(eq(AuditLogService.DELETE), eq(AuditLogService.CATEGORY),
                any(), any(), any(), isNull());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Category category(String name, String slug) {
        return Category.builder()
                .id(UUID.randomUUID())
                .name(name).slug(slug)
                .active(true).sortOrder(0)
                .build();
    }
}
