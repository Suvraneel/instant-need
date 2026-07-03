package com.b2b.instantneed.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
        return writeBytes(file.getBytes(), subdir, filename);
    }

    @Override
    public StoredImage storeWithThumbnail(MultipartFile file, String subdir) throws IOException {
        validate(file);
        byte[] bytes = file.getBytes();
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
        String url = writeBytes(bytes, subdir, filename);

        String thumbnailUrl = ImageThumbnailer.resize(bytes, file.getContentType())
                .map(thumbBytes -> {
                    try {
                        return writeBytes(thumbBytes, subdir, thumbnailFilename(filename));
                    } catch (IOException e) {
                        log.warn("[STORAGE] Thumbnail write failed for {}: {}", filename, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);

        return new StoredImage(url, thumbnailUrl);
    }

    private String writeBytes(byte[] bytes, String subdir, String filename) throws IOException {
        Path targetDir = uploadRoot.resolve(subdir);
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(filename);
        Files.write(targetPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("[STORAGE] Saved {} ({} bytes) → {}", filename, bytes.length, targetPath);
        return baseUrl + "/" + subdir + "/" + filename;
    }

    private static String thumbnailFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot == -1 ? filename : filename.substring(0, dot)) + "_thumb.jpg";
    }

    @Override
    public String storeBytes(byte[] data, String subdir, String filename) throws IOException {
        return writeBytes(data, subdir, filename);
    }

    @Override
    public String storeBytes(byte[] data, String subdir, String filename, String contentType) throws IOException {
        // Content type is inferred from the file extension by the static resource
        // handler for local storage — nothing further to configure here.
        return writeBytes(data, subdir, filename);
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
