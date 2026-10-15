package com.fss.backend.file;

import java.io.IOException;

/**
 * Binary storage for uploaded files. Implementations must make {@link #delete(String)} idempotent: deleting a
 * missing object is a success, so cleanup can safely retry.
 */
public interface FileStorage {
    /** Provider name stored on metadata, e.g. {@code local} or {@code r2}. */
    String provider();

    /** Maps the provider-independent relative path to the key used inside this storage. */
    String objectKey(String relativePath);

    void put(String objectKey, byte[] bytes, String contentType) throws IOException;

    void delete(String objectKey) throws IOException;
}
