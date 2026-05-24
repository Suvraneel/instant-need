package com.b2b.instantneed.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files on the local filesystem and returns URLs served by the
 * Spring MVC static-resource handler at {@code /uploads/**}.
 *
 * <p>In production, replace with an S3-backed implementation and set
 * {@code STORAGE_BASE_URL} to your CDN prefix.</p>
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_BYTES = 5 * 1024 * 1024; // 5 MB

    private final Path uploadRoot;
    private final String baseUrl;

    public LocalStorageService(Path uploadRoot, String baseUrl) {
        this.uploadRoot = uploadRoot;
        this.baseUrl    = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String store(MultipartFile file, String subdir) throws IOException {
        validate(file);

        String extension  = extension(file.getOriginalFilename());
        String filename   = UUID.randomUUID() + extension;
        Path   targetDir  = uploadRoot.resolve(subdir);
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("[STORAGE] Saved {} ({} bytes) → {}", filename, file.getSize(), targetPath);

        // Return a URL relative to the baseUrl — clients fetch it directly
        return baseUrl + "/" + subdir + "/" + filename;
    }

    @Override
    public void delete(String publicUrl) {
        try {
            // Convert "http://host/uploads/products/uuid/file.jpg" → "products/uuid/file.jpg"
            String path = publicUrl.replace(baseUrl + "/", "");
            Path   file = uploadRoot.resolve(path);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("[STORAGE] Deleted {}", file);
            }
        } catch (Exception e) {
            log.warn("[STORAGE] Could not delete {}: {}", publicUrl, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new StorageException("File exceeds maximum allowed size of 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new StorageException(
                    "Unsupported file type '" + contentType + "'. Allowed: JPEG, PNG, WebP, GIF");
        }
    }

    private static String extension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
