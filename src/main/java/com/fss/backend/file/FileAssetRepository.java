package com.fss.backend.file;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FileAssetRepository {
    enum ActivationResult { ACTIVATED, NOT_FOUND, UNAVAILABLE }

    void createUploading(FileDocument file);

    /** UPLOADING -> INACTIVE once the object is stored. Returns false if the record vanished meanwhile. */
    boolean markUploaded(UUID id);

    FileAsset findById(UUID id);

    FileAsset findByPath(String path);

    Map<UUID, String> findPathsByIds(Collection<UUID> ids);

    /** INACTIVE/ACTIVE -> ACTIVE. Files that are uploading or being deleted cannot be attached. */
    ActivationResult activate(UUID id, UUID adminId);

    /** ACTIVE -> INACTIVE, restarting the cleanup TTL. */
    void deactivate(UUID id, UUID adminId);

    /**
     * Atomically claims one file for deletion (multi-instance safe): an INACTIVE file unused since before
     * {@code inactiveCutoff}, an UPLOADING record older than {@code uploadingCutoff}, or a DELETING file whose lease expired
     * and whose retry time has come.
     */
    FileDocument claimForDeletion(Instant now, Instant inactiveCutoff, Instant uploadingCutoff, Instant leaseUntil);

    /** Gives a claimed file back (it turned out to be referenced); its cleanup TTL restarts at {@code at}. */
    void restoreClaimed(UUID id, String status, Instant at);

    /** Keeps the metadata (status DELETING) and schedules another attempt. */
    void recordDeleteFailure(UUID id, String error, Instant retryAt);

    /** Removes metadata of a claimed file after its object is gone. */
    void deleteClaimed(UUID id);

    /** Best-effort removal of a record whose upload failed. */
    void deleteUploading(UUID id);
}
