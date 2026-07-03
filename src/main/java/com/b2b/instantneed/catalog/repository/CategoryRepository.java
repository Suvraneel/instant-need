package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    List<Category> findAllByImageUrlIsNotNullAndThumbnailUrlIsNull();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL AND c.active = true ORDER BY c.sortOrder")
    List<Category> findRootCategoriesWithChildren();
}
