package com.b2b.instantneed.common.storage;

/**
 * Thrown when an uploaded file fails validation (bad type, too large, etc.)
 * or when the storage backend signals an unrecoverable error.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
