package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.file.FileDocument;
import com.fss.backend.file.FileService;
import com.fss.backend.file.FileStatus;
import com.fss.backend.file.FileStorage;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileLifecycleIntegrationTest extends MongoIntegrationTest {
    private static final Path UPLOAD_ROOT = Path.of("build/tmp/it-uploads");

    @Autowired
    private FileService fileService;
    @SpyBean
    private FileStorage storage;

    @Test
    void unusedUploadsAreDeletedOnlyAfterTtlAndReferencedFilesAreKept() throws Exception {
        JsonNode unused = uploadImage(TestImages.png(8, 8, false), "unused.png");
        JsonNode used = uploadImage(TestImages.png(8, 8, false), "used.png");
        assertTrue(Files.exists(UPLOAD_ROOT.resolve(unused.get("path").asText())));

        String brandId = createId("/api/admin/brands", Map.of("name", unique("Brand"), "fileId", used.get("fileId").asText()));

        drainCleanup(Instant.now());
        assertNotNull(file(unused), "TTL (24h) not reached yet");

        drainCleanup(Instant.now().plus(Duration.ofHours(25)));
        assertNull(file(unused));
        assertFalse(Files.exists(UPLOAD_ROOT.resolve(unused.get("path").asText())));
        assertEquals(FileStatus.ACTIVE, file(used).status());
        assertTrue(Files.exists(UPLOAD_ROOT.resolve(used.get("path").asText())));

        // Released by an update: INACTIVE again and the TTL restarts from the release time.
        JsonNode replacement = uploadImage(TestImages.png(8, 8, false), "replacement.png");
        mvc.perform(admin(jsonBody(put("/api/admin/brands/" + brandId), Map.of("name", "B2", "fileId", replacement.get("fileId").asText()))))
                .andExpect(status().isOk());
        assertEquals(FileStatus.INACTIVE, file(used).status());
        assertEquals(FileStatus.ACTIVE, file(replacement).status());
        drainCleanup(Instant.now().plus(Duration.ofHours(1)));
        assertNotNull(file(used));
        drainCleanup(Instant.now().plus(Duration.ofHours(25)));
        assertNull(file(used));
        assertNotNull(file(replacement));
    }

    @Test
    void filesOfSoftDeletedRecordsAreNotPhysicallyDeleted() throws Exception {
        JsonNode logo = uploadImage(TestImages.png(8, 8, false), "logo.png");
        String brandId = createId("/api/admin/brands", Map.of("name", unique("Brand"), "fileId", logo.get("fileId").asText()));
        mvc.perform(admin(delete("/api/admin/brands/" + brandId))).andExpect(status().isOk());
        assertEquals(FileStatus.INACTIVE, file(logo).status());

        drainCleanup(Instant.now().plus(Duration.ofHours(25)));

        // Same guarantee as the old SQL cleanup (NOT EXISTS on every referencing row, deleted or not).
        assertEquals(FileStatus.INACTIVE, file(logo).status());
        assertTrue(Files.exists(UPLOAD_ROOT.resolve(logo.get("path").asText())));
    }

    @Test
    void fileBeingDeletedCannotBeAttached() throws Exception {
        JsonNode image = uploadImage(TestImages.png(8, 8, false), "x.png");
        mongo.updateFirst(Query.query(Criteria.where("_id").is(UUID.fromString(image.get("fileId").asText()))),
                Update.update("status", FileStatus.DELETING), FileDocument.class);

        mvc.perform(admin(jsonBody(post("/api/admin/brands"), Map.of("name", unique("Brand"), "fileId", image.get("fileId").asText()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is no longer available, please re-upload: " + image.get("fileId").asText()));
        mvc.perform(admin(jsonBody(post("/api/admin/brands"), Map.of("name", unique("Brand"), "fileId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentCleanupRunsDeleteEachObjectExactlyOnce() throws Exception {
        List<JsonNode> uploads = new ArrayList<>();
        for (int i = 0; i < 12; i++) uploads.add(uploadImage(TestImages.png(4, 4, false), "c" + i + ".png"));
        Instant future = Instant.now().plus(Duration.ofHours(25));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                start.await();
                drainCleanup(future);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        for (JsonNode upload : uploads) {
            assertNull(file(upload));
            verify(storage, times(1)).delete(eq(upload.get("path").asText()));
        }
    }

    @Test
    void failedStorageDeleteKeepsMetadataAndIsRetried() throws Exception {
        JsonNode upload = uploadImage(TestImages.png(4, 4, false), "retry.png");
        String key = upload.get("path").asText();
        doThrow(new IOException("storage unavailable")).when(storage).delete(key);

        drainCleanup(Instant.now().plus(Duration.ofHours(25)));
        FileDocument pending = file(upload);
        assertEquals(FileStatus.DELETING, pending.status());
        assertEquals(1, pending.deleteAttempts());
        assertTrue(pending.lastError().contains("storage unavailable"));

        reset(storage);
        drainCleanup(pending.leaseUntil().plusSeconds(1));
        assertNull(file(upload));
    }

    @Test
    void staleUploadingRecordsAreCollected() throws Exception {
        UUID id = UUID.randomUUID();
        Instant old = Instant.now().minus(Duration.ofHours(2));
        String path = "2026-01-01/" + id + "-orphan.png";
        Files.createDirectories(UPLOAD_ROOT.resolve("2026-01-01"));
        Files.write(UPLOAD_ROOT.resolve(path), new byte[]{1});
        mongo.insert(new FileDocument(id, path, path, "local", FileStatus.UPLOADING, "image/png", 1L, 1, 1, null, null,
                old, old, old, null, 0, null));

        drainCleanup(Instant.now());

        assertNull(mongo.findById(id, FileDocument.class));
        assertFalse(Files.exists(UPLOAD_ROOT.resolve(path)));
    }

    private void drainCleanup(Instant now) {
        for (int round = 0; fileService.cleanupInactiveFiles(now) > 0; round++) {
            if (round > 20) throw new AssertionError("cleanup keeps reclaiming the same files");
        }
    }

    private FileDocument file(JsonNode upload) {
        return mongo.findById(UUID.fromString(upload.get("fileId").asText()), FileDocument.class);
    }

    private String createId(String url, Object payload) throws Exception {
        return body(mvc.perform(admin(jsonBody(post(url), payload))).andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
    }
}
