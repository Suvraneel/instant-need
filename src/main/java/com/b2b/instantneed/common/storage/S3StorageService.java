package com.b2b.instantneed.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files in an AWS S3 bucket and returns publicly accessible
 * CloudFront URLs.
 *
 * <p>Activate with env var {@code STORAGE_TYPE=s3}. Required env vars:
 * <ul>
 *   <li>{@code S3_BUCKET} — target bucket name</li>
 *   <li>{@code STORAGE_BASE_URL} — CloudFront distribution URL (no trailing slash)</li>
 *   <li>{@code AWS_REGION} — e.g. {@code ap-northeast-2}</li>
 * </ul>
 * Authentication uses the AWS default credential provider chain (IAM role,
 * {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}, or instance profile).
 * </p>
 */
@Slf4j
public class S3StorageService implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    private final S3Client  s3Client;
    private final String    bucket;
    private final String    baseUrl;

    public S3StorageService(S3Client s3Client, String bucket, String baseUrl) {
        this.s3Client = s3Client;
        this.bucket   = bucket;
        this.baseUrl  = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // ── StorageService ─────────────────────────────────────────────────────────

    @Override
    public String store(MultipartFile file, String subdir) throws IOException {
        validate(file);
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
        return putBytes(file.getBytes(), subdir, filename, file.getContentType());
    }

    @Override
    public StoredImage storeWithThumbnail(MultipartFile file, String subdir) throws IOException {
        validate(file);
        byte[] bytes = file.getBytes();
        String filename = UUID.randomUUID() + extension(file.getOriginalFilename());
        String url = putBytes(bytes, subdir, filename, file.getContentType());

        String thumbnailUrl = ImageThumbnailer.resize(bytes, file.getContentType())
                .map(thumbBytes -> {
                    try {
                        return putBytes(thumbBytes, subdir, thumbnailFilename(filename), "image/jpeg");
                    } catch (Exception e) {
                        log.warn("[S3] Thumbnail upload failed for {}: {}", filename, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);

        return new StoredImage(url, thumbnailUrl);
    }

    private String putBytes(byte[] bytes, String subdir, String filename, String contentType) {
        String s3Key = subdir + "/" + filename;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info("[S3] Uploaded {} ({} bytes) → s3://{}/{}", filename, bytes.length, bucket, s3Key);
        return baseUrl + "/" + s3Key;
    }

    private static String thumbnailFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot == -1 ? filename : filename.substring(0, dot)) + "_thumb.jpg";
    }

    @Override
    public String storeBytes(byte[] data, String subdir, String filename) {
        // Preserved for the existing invoice-PDF caller.
        return putBytes(data, subdir, filename, "application/pdf");
    }

    @Override
    public String storeBytes(byte[] data, String subdir, String filename, String contentType) {
        return putBytes(data, subdir, filename, contentType);
    }

    @Override
    public void delete(String publicUrl) {
        try {
            // "https://cdn.example.com/products/uuid/file.jpg" → "products/uuid/file.jpg"
            String s3Key = publicUrl.replace(baseUrl + "/", "");

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());

            log.info("[S3] Deleted s3://{}/{}", bucket, s3Key);
        } catch (Exception e) {
            log.warn("[S3] Could not delete {}: {}", publicUrl, e.getMessage());
        }
    }

    @Override
    public byte[] retrieve(String publicUrl) throws IOException {
        String s3Key = publicUrl.replace(baseUrl + "/", "");
        try (var in = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build())) {
            return in.readAllBytes();
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
