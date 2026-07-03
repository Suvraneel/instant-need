package com.b2b.instantneed.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Abstraction over file storage backends (local filesystem for dev, S3 for prod).
 */
public interface StorageService {

    /**
     * Persist the file and return its public URL.
     *
     * @param file   the uploaded file
     * @param subdir logical subdirectory, e.g. {@code "products/uuid"}
     * @return       publicly accessible URL for the stored file
     * @throws IOException      if the file cannot be written
     * @throws StorageException if the file type or size is rejected
     */
    String store(MultipartFile file, String subdir) throws IOException;

    /**
     * Persist the file and also generate+store a downscaled JPEG thumbnail
     * (max ~480px side) for lightweight list-view rendering.
     *
     * <p>If the source format can't be decoded for resizing (e.g. WebP has no
     * built-in JDK reader), {@link StoredImage#thumbnailUrl()} is {@code null}
     * and callers should fall back to the original {@link StoredImage#url()}.</p>
     *
     * @param file   the uploaded file
     * @param subdir logical subdirectory, e.g. {@code "products/uuid"}
     * @return       the original and (if generated) thumbnail URLs
     * @throws IOException      if the file cannot be written
     * @throws StorageException if the file type or size is rejected
     */
    StoredImage storeWithThumbnail(MultipartFile file, String subdir) throws IOException;

    /**
     * Persist raw bytes (e.g. a generated PDF) and return its public URL.
     *
     * @param data     the raw file bytes
     * @param subdir   logical subdirectory, e.g. {@code "invoices"}
     * @param filename exact filename to use (including extension)
     * @return         publicly accessible URL for the stored file
     * @throws IOException if the file cannot be written
     */
    String storeBytes(byte[] data, String subdir, String filename) throws IOException;

    /**
     * Persist raw bytes with an explicit content type (e.g. a generated thumbnail).
     *
     * @param data        the raw file bytes
     * @param subdir      logical subdirectory, e.g. {@code "products/uuid"}
     * @param filename    exact filename to use (including extension)
     * @param contentType MIME type to store the object with
     * @return            publicly accessible URL for the stored file
     * @throws IOException if the file cannot be written
     */
    String storeBytes(byte[] data, String subdir, String filename, String contentType) throws IOException;

    /**
     * Delete the file identified by its public URL.
     * Implementations should log-and-swallow if the file is already gone.
     *
     * @param publicUrl URL that was previously returned by {@link #store}
     */
    void delete(String publicUrl);

    /**
     * Read back the raw bytes of a file identified by its public URL.
     * Used to stream PII-sensitive content (e.g. invoices) through an
     * authenticated API endpoint rather than exposing it at a public URL.
     *
     * @param publicUrl URL that was previously returned by {@link #store} or {@link #storeBytes}
     * @throws IOException if the file cannot be read (including "not found")
     */
    byte[] retrieve(String publicUrl) throws IOException;

    record StoredImage(String url, String thumbnailUrl) {}
}
