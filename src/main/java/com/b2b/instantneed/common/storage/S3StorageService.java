package com.b2b.instantneed.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
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

        String extension = extension(file.getOriginalFilename());
        String filename  = UUID.randomUUID() + extension;
        String s3Key     = subdir + "/" + filename;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        log.info("[S3] Uploaded {} ({} bytes) → s3://{}/{}", filename, file.getSize(), bucket, s3Key);

        return baseUrl + "/" + s3Key;
    }

    @Override
    public String storeBytes(byte[] data, String subdir, String filename) {
        String s3Key = subdir + "/" + filename;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType("application/pdf")
                .contentLength((long) data.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.info("[S3] Uploaded {} ({} bytes) → s3://{}/{}", filename, data.length, bucket, s3Key);
        return baseUrl + "/" + s3Key;
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
