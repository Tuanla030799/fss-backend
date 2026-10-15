package com.fss.backend.file;

import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Upload pipeline and storage cleanup.
 *
 * <p>Upload: validate + process image -> insert metadata (UPLOADING) -> put object -> mark INACTIVE. Metadata is
 * written <em>before</em> the object, so a crash at any point leaves a record that cleanup can find; nothing in R2 is
 * ever untracked. Business records attach the file later by fileId (status ACTIVE).
 *
 * <p>Cleanup: one file at a time is claimed atomically (status DELETING + lease), so several dynos can run the job
 * concurrently. References are re-checked after the claim; while DELETING a file cannot be attached. Metadata is
 * removed only after the storage delete succeeded; failures are retried with backoff.
 */
@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileAssetRepository repository;
    private final FileStorage storage;
    private final CurrentAdmin currentAdmin;
    private final FileUrlService fileUrlService;
    private final ImageProcessor imageProcessor;
    private final FileUsageIndex usageIndex;
    private final ZoneId zone;
    private final Duration inactiveTtl;
    private final Duration uploadingTtl;
    private final Duration lease;
    private final int batchSize;

    public FileService(FileAssetRepository repository, FileStorage storage, CurrentAdmin currentAdmin,
                       FileUrlService fileUrlService, ImageProcessor imageProcessor, FileUsageIndex usageIndex,
                       @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone,
                       @Value("${app.file-cleanup.inactive-ttl:PT24H}") Duration inactiveTtl,
                       @Value("${app.file-cleanup.uploading-ttl:PT1H}") Duration uploadingTtl,
                       @Value("${app.file-cleanup.lease:PT10M}") Duration lease,
                       @Value("${app.file-cleanup.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.storage = storage;
        this.currentAdmin = currentAdmin;
        this.fileUrlService = fileUrlService;
        this.imageProcessor = imageProcessor;
        this.usageIndex = usageIndex;
        this.zone = ZoneId.of(timeZone);
        this.inactiveTtl = inactiveTtl;
        this.uploadingTtl = uploadingTtl;
        this.lease = lease;
        this.batchSize = Math.max(1, batchSize);
    }

    public Map<String, String> upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException("File is empty");
        }
        ImageProcessor.Result image = imageProcessor.process(file.getBytes());

        UUID id = UUID.randomUUID();
        String relativePath = LocalDate.now(zone) + "/" + id + "-" + safeBaseName(file.getOriginalFilename()) + "." + image.extension();
        String objectKey = storage.objectKey(relativePath);
        Instant now = Instant.now();
        UUID adminId = currentAdmin.idOrNull();
        repository.createUploading(new FileDocument(id, relativePath, objectKey, storage.provider(), FileStatus.UPLOADING,
                image.contentType(), (long) image.bytes().length, image.width(), image.height(),
                adminId, adminId, now, now, now, null, 0, null));

        try {
            storage.put(objectKey, image.bytes(), image.contentType());
        } catch (IOException | RuntimeException ex) {
            log.error("File upload to storage failed fileId={} key={} provider={}", id, objectKey, storage.provider(), ex);
            discardFailedUpload(id, null);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is temporarily unavailable, please retry");
        }

        boolean marked;
        try {
            marked = repository.markUploaded(id);
        } catch (RuntimeException ex) {
            log.error("File metadata update failed after upload fileId={} key={}", id, objectKey, ex);
            discardFailedUpload(id, objectKey);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File metadata could not be saved, please retry");
        }
        if (!marked) {
            discardFailedUpload(id, objectKey);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "File metadata could not be saved, please retry");
        }
        log.info("File uploaded fileId={} path={} provider={} size={} resized={}", id, relativePath, storage.provider(),
                image.bytes().length, image.resized());

        Map<String, String> data = new HashMap<>();
        data.put("fileId", id.toString());
        data.put("path", relativePath);
        data.put("url", fileUrlService.publicUrl(relativePath));
        data.put("resized", Boolean.toString(image.resized()));
        if (image.width() != null) data.put("width", image.width().toString());
        if (image.height() != null) data.put("height", image.height().toString());
        return data;
    }

    /** Compensation for a failed upload. If it fails too, the UPLOADING record is collected by cleanup later. */
    private void discardFailedUpload(UUID id, String objectKey) {
        try {
            if (objectKey != null) storage.delete(objectKey);
            repository.deleteUploading(id);
        } catch (Exception ex) {
            log.warn("Compensation after failed upload did not complete fileId={}; cleanup will retry: {}", id, ex.toString());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${app.file-cleanup.fixed-delay-ms:300000}",
            initialDelayString = "${app.file-cleanup.initial-delay-ms:60000}")
    public void scheduledCleanup() {
        try {
            int processed = cleanupInactiveFiles(Instant.now());
            if (processed > 0) log.info("File cleanup processed {} file(s)", processed);
        } catch (RuntimeException ex) {
            log.warn("File cleanup run failed: {}", ex.toString());
        }
    }

    /** Processes up to one batch of claimable files; returns how many were claimed. */
    public int cleanupInactiveFiles(Instant now) {
        int processed = 0;
        while (processed < batchSize) {
            FileDocument file = repository.claimForDeletion(now, now.minus(inactiveTtl), now.minus(uploadingTtl), now.plus(lease));
            if (file == null) break;
            processed++;
            cleanupClaimed(file, now);
        }
        return processed;
    }

    private void cleanupClaimed(FileDocument file, Instant now) {
        boolean wasUploading = FileStatus.UPLOADING.equals(file.status());
        if (!wasUploading && usageIndex.isReferencedAnywhere(file.id(), file.path())) {
            log.info("File cleanup skipped referenced fileId={} path={}", file.id(), file.path());
            repository.restoreClaimed(file.id(), FileStatus.INACTIVE, now);
            return;
        }
        String key = file.objectKey() != null ? file.objectKey() : storage.objectKey(file.path());
        try {
            if (file.storageProvider() != null && !file.storageProvider().equals(storage.provider())) {
                log.warn("File cleanup fileId={} stored in provider={} but active provider={}; deleting metadata only",
                        file.id(), file.storageProvider(), storage.provider());
            } else {
                storage.delete(key);
            }
            repository.deleteClaimed(file.id());
            log.info("File deleted fileId={} key={}", file.id(), key);
        } catch (IOException | RuntimeException ex) {
            int attempts = file.deleteAttempts() == null ? 1 : file.deleteAttempts() + 1;
            Instant base = now.isAfter(Instant.now()) ? now : Instant.now();
            Instant retryAt = base.plus(retryDelay(attempts));
            log.warn("File delete failed fileId={} key={} attempt={} retryAt={} cause={}", file.id(), key, attempts, retryAt, ex.toString());
            repository.recordDeleteFailure(file.id(), ex.toString(), retryAt);
        }
    }

    static Duration retryDelay(int attempts) {
        long minutes = Math.min(24 * 60, (long) Math.pow(2, Math.min(attempts, 11)));
        return Duration.ofMinutes(minutes);
    }

    static String safeBaseName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "")
                .toLowerCase(Locale.ROOT);
        if (ascii.length() > 80) ascii = ascii.substring(0, 80);
        return ascii.isBlank() ? "image" : ascii;
    }
}
