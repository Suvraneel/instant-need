package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.catalog.entity.Category;
import com.b2b.instantneed.catalog.entity.ProductImage;
import com.b2b.instantneed.catalog.repository.CategoryRepository;
import com.b2b.instantneed.catalog.repository.ProductImageRepository;
import com.b2b.instantneed.common.storage.ImageThumbnailer;
import com.b2b.instantneed.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One-off maintenance job: generates thumbnails for categories/product images
 * that predate {@link StorageService#storeWithThumbnail}, so existing catalog
 * entries also get the lightweight list-view thumbnail. Safe to re-run —
 * only processes rows where {@code thumbnailUrl} is still null.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminThumbnailBackfillService {

    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final StorageService storageService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public BackfillResult backfill() {
        BackfillCounts categories = backfillCategories();
        BackfillCounts productImages = backfillProductImages();
        return new BackfillResult(
                categories.updated(), categories.failed(),
                productImages.updated(), productImages.failed());
    }

    private BackfillCounts backfillCategories() {
        List<Category> pending = categoryRepository.findAllByImageUrlIsNotNullAndThumbnailUrlIsNull();
        int updated = 0, failed = 0;
        for (Category category : pending) {
            Optional<String> thumbnailUrl = generateThumbnail(category.getImageUrl(), "categories/" + category.getId());
            if (thumbnailUrl.isPresent()) {
                category.setThumbnailUrl(thumbnailUrl.get());
                categoryRepository.save(category);
                updated++;
            } else {
                failed++;
            }
        }
        log.info("[BACKFILL] Categories: {} updated, {} failed (of {} pending)", updated, failed, pending.size());
        return new BackfillCounts(updated, failed);
    }

    private BackfillCounts backfillProductImages() {
        List<ProductImage> pending = productImageRepository.findAllByImageUrlIsNotNullAndThumbnailUrlIsNull();
        int updated = 0, failed = 0;
        for (ProductImage image : pending) {
            Optional<String> thumbnailUrl = generateThumbnail(image.getImageUrl(), "products/" + image.getProduct().getId());
            if (thumbnailUrl.isPresent()) {
                image.setThumbnailUrl(thumbnailUrl.get());
                productImageRepository.save(image);
                updated++;
            } else {
                failed++;
            }
        }
        log.info("[BACKFILL] Product images: {} updated, {} failed (of {} pending)", updated, failed, pending.size());
        return new BackfillCounts(updated, failed);
    }

    private Optional<String> generateThumbnail(String imageUrl, String subdir) {
        try {
            byte[] original = download(imageUrl);
            String contentType = guessContentType(imageUrl);
            return ImageThumbnailer.resize(original, contentType).flatMap(thumbBytes -> {
                try {
                    String filename = "backfill-" + UUID.randomUUID() + "_thumb.jpg";
                    return Optional.of(storageService.storeBytes(thumbBytes, subdir, filename, "image/jpeg"));
                } catch (IOException e) {
                    log.warn("[BACKFILL] Failed to store thumbnail for {}: {}", imageUrl, e.getMessage());
                    return Optional.empty();
                }
            });
        } catch (Exception e) {
            log.warn("[BACKFILL] Failed to fetch/resize {}: {}", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] download(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status " + response.statusCode() + " fetching " + imageUrl);
        }
        return response.body();
    }

    private static String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private record BackfillCounts(int updated, int failed) {}

    public record BackfillResult(
            int categoriesUpdated, int categoriesFailed,
            int productImagesUpdated, int productImagesFailed
    ) {}
}
