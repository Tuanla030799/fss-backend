package com.fss.backend.file;

import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.common.ApiException;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {
    private FileAssetRepository repository;
    private FileStorage storage;
    private FileUsageIndex usageIndex;
    private FileService service;

    @BeforeEach
    void setUp() {
        repository = mock(FileAssetRepository.class);
        storage = mock(FileStorage.class);
        usageIndex = mock(FileUsageIndex.class);
        when(storage.provider()).thenReturn("r2");
        when(storage.objectKey(anyString())).thenAnswer(invocation -> "files/" + invocation.getArgument(0));
        when(repository.markUploaded(any())).thenReturn(true);
        service = service(new FileUrlService("https://img.example.com/files/"));
    }

    private FileService service(FileUrlService urls) {
        ImageProcessor processor = new ImageProcessor(1600, 1600, 40_000_000L, 0.85f, 2, 1000);
        processor.registerPlugins();
        return new FileService(repository, storage, mock(CurrentAdmin.class), urls, processor, usageIndex,
                "Asia/Ho_Chi_Minh", Duration.ofHours(24), Duration.ofHours(1), Duration.ofMinutes(10), 50);
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{});
        assertThrows(ApiException.class, () -> service.upload(file));
    }

    @Test
    void uploadShouldStoreMetadataBeforeObjectAndReturnSameContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "Ảnh Giày Đẹp.PNG", "image/png", TestImages.png(10, 5, false));

        Map<String, String> result = service.upload(file);

        ArgumentCaptor<FileDocument> created = ArgumentCaptor.forClass(FileDocument.class);
        var order = org.mockito.Mockito.inOrder(repository, storage);
        order.verify(repository).createUploading(created.capture());
        order.verify(storage).put(eq("files/" + created.getValue().path()), any(byte[].class), eq("image/png"));
        order.verify(repository).markUploaded(created.getValue().id());

        FileDocument doc = created.getValue();
        assertEquals(FileStatus.UPLOADING, doc.status());
        assertEquals("r2", doc.storageProvider());
        assertTrue(doc.path().matches("\\d{4}-\\d{2}-\\d{2}/[0-9a-f-]{36}-anh-giay-dep\\.png"), doc.path());
        assertEquals(doc.id().toString(), result.get("fileId"));
        assertTrue(UUID.fromString(result.get("fileId")) != null);
        assertEquals(doc.path(), result.get("path"));
        assertEquals("https://img.example.com/files/" + doc.path(), result.get("url"));
        assertEquals("false", result.get("resized"));
        assertEquals("10", result.get("width"));
        assertEquals("5", result.get("height"));
    }

    @Test
    void uploadShouldRejectSpoofedMimeType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThrows(ApiException.class, () -> service.upload(file));
        verify(repository, never()).createUploading(any());
    }

    @Test
    void storageFailureShouldRemoveMetadataAndReturn503() throws Exception {
        doThrow(new IOException("R2 down")).when(storage).put(anyString(), any(byte[].class), anyString());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", TestImages.png(4, 4, false));

        ApiException ex = assertThrows(ApiException.class, () -> service.upload(file));

        assertEquals(503, ex.status().value());
        verify(repository).deleteUploading(any(UUID.class));
        verify(repository, never()).markUploaded(any());
    }

    @Test
    void metadataFailureAfterUploadShouldDeleteObject() throws Exception {
        when(repository.markUploaded(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("mongo down"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", TestImages.png(4, 4, false));

        ApiException ex = assertThrows(ApiException.class, () -> service.upload(file));

        assertEquals(503, ex.status().value());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(byte[].class), anyString());
        verify(storage).delete(key.getValue());
    }

    @Test
    void cleanupShouldDeleteObjectThenMetadataForUnusedFile() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.claimForDeletion(any(), any(), any(), any()))
                .thenReturn(file(id, FileStatus.INACTIVE, 0)).thenReturn(null);

        int processed = service.cleanupInactiveFiles(Instant.now());

        assertEquals(1, processed);
        var order = org.mockito.Mockito.inOrder(storage, repository);
        order.verify(storage).delete("files/2026-01-01/a.png");
        order.verify(repository).deleteClaimed(id);
    }

    @Test
    void cleanupShouldNeverDeleteReferencedFile() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.claimForDeletion(any(), any(), any(), any()))
                .thenReturn(file(id, FileStatus.INACTIVE, 0)).thenReturn(null);
        when(usageIndex.isReferencedAnywhere(id, "2026-01-01/a.png")).thenReturn(true);

        service.cleanupInactiveFiles(Instant.now());

        verify(storage, never()).delete(anyString());
        verify(repository, never()).deleteClaimed(any());
        verify(repository).restoreClaimed(eq(id), eq(FileStatus.INACTIVE), any(Instant.class));
    }

    @Test
    void failedStorageDeleteShouldKeepMetadataAndScheduleRetry() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.claimForDeletion(any(), any(), any(), any()))
                .thenReturn(file(id, FileStatus.INACTIVE, 2)).thenReturn(null);
        doThrow(new IOException("R2 timeout")).when(storage).delete(anyString());

        service.cleanupInactiveFiles(Instant.now());

        verify(repository, never()).deleteClaimed(any());
        verify(repository).recordDeleteFailure(eq(id), anyString(), any(Instant.class));
    }

    @Test
    void retryDelayGrowsAndIsCapped() {
        assertEquals(Duration.ofMinutes(2), FileService.retryDelay(1));
        assertEquals(Duration.ofMinutes(1024), FileService.retryDelay(10));
        assertEquals(Duration.ofMinutes(1440), FileService.retryDelay(50));
    }

    private static FileDocument file(UUID id, String status, int attempts) {
        Instant old = Instant.now().minus(Duration.ofDays(3));
        return new FileDocument(id, "2026-01-01/a.png", "files/2026-01-01/a.png", "r2", status, "image/png", 3L, 1, 1,
                null, null, old, old, old, null, attempts, null);
    }
}
