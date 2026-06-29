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
     * Delete the file identified by its public URL.
     * Implementations should log-and-swallow if the file is already gone.
     *
     * @param publicUrl URL that was previously returned by {@link #store}
     */
    void delete(String publicUrl);
}
