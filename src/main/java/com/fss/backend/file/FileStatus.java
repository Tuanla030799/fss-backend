package com.fss.backend.file;

/**
 * File metadata lifecycle.
 *
 * <pre>
 * UPLOADING --(object stored)--> INACTIVE <--(attach / release)--> ACTIVE
 *     |                              |
 *     | (stale: upload crashed)      | (unused longer than the configured TTL)
 *     v                              v
 *                 DELETING --(object deleted)--> metadata removed
 * </pre>
 *
 * A file is only ever deleted from storage while it is DELETING, and a DELETING file can no longer be attached.
 */
public final class FileStatus {
    public static final String UPLOADING = "UPLOADING";
    public static final String INACTIVE = "INACTIVE";
    public static final String ACTIVE = "ACTIVE";
    public static final String DELETING = "DELETING";

    private FileStatus() {
    }
}
