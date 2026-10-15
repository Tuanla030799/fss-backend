package com.fss.backend.content.html;

import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.file.FileAsset;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.file.FileUrlService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HtmlContentImageUsageServiceTest {

    @Test
    void activateImagesShouldMarkLocalUploadedImagesActive() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
        UUID adminId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(currentAdmin.idOrNull()).thenReturn(adminId);
        when(repository.activate(any(), any())).thenReturn(FileAssetRepository.ActivationResult.ACTIVATED);
        when(repository.findByPath("2026-05-06/a.png"))
                .thenReturn(new FileAsset(fileId, "2026-05-06/a.png", "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<p>x</p><img src=\"/files/2026-05-06/a.png\">");

        verify(repository).activate(eq(fileId), eq(adminId));
    }

    @Test
    void activateImagesShouldMarkAbsoluteLocalUrlsWithSpacesActive() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
        UUID adminId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String path = "2026-07-27/c60b717b-Screenshot from 2026-07-23.png";
        when(currentAdmin.idOrNull()).thenReturn(adminId);
        when(repository.activate(any(), any())).thenReturn(FileAssetRepository.ActivationResult.ACTIVATED);
        when(repository.findByPath(path))
                .thenReturn(new FileAsset(fileId, path, "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<img src=\"http://localhost:8080/files/" + path + "\">");

        verify(repository).activate(eq(fileId), eq(adminId));
    }

    @Test
    void activateImagesShouldDecodeEncodedFilePaths() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
        UUID adminId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String path = "2026-07-27/c60b717b-Screenshot from 2026-07-23.png";
        when(currentAdmin.idOrNull()).thenReturn(adminId);
        when(repository.activate(any(), any())).thenReturn(FileAssetRepository.ActivationResult.ACTIVATED);
        when(repository.findByPath(path))
                .thenReturn(new FileAsset(fileId, path, "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<img src=\"/files/2026-07-27/c60b717b-Screenshot%20from%202026-07-23.png\">");

        verify(repository).activate(eq(fileId), eq(adminId));
    }

    @Test
    void rewriteForOutputShouldPointLegacyLinksAtCurrentStorage() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        EcommerceSupport support = new EcommerceSupport(mock(CurrentAdmin.class), repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support,
                new FileUrlService("https://img.example.com/files"),
                new HtmlSanitizerService("old.example.com", "https://img.example.com/files"));

        String html = service.rewriteForOutput("<p>a</p><img src=\"https://old.example.com/files/2026-01-01/a b.png\" alt=\"x\">"
                + "<img src=\"/files/2026-01-02/c.png\"><img src=\"https://evil.example/files/x.png\">");

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("src=\"https://img.example.com/files/2026-01-01/a%20b.png\""), html);
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("src=\"https://img.example.com/files/2026-01-02/c.png\""), html);
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("src=\"https://evil.example/files/x.png\""), html);
        org.junit.jupiter.api.Assertions.assertEquals(
                Set.of("2026-01-01/a b.png", "2026-01-02/c.png", "x.png"), service.extractFilePaths(html));
    }

    @Test
    void activateImagesShouldRejectFilesBeingDeleted() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        UUID fileId = UUID.randomUUID();
        when(repository.findByPath("2026-05-06/a.png")).thenReturn(new FileAsset(fileId, "2026-05-06/a.png", "DELETING", null));
        when(repository.activate(any(), any())).thenReturn(FileAssetRepository.ActivationResult.UNAVAILABLE);
        EcommerceSupport support = new EcommerceSupport(mock(CurrentAdmin.class), repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        org.junit.jupiter.api.Assertions.assertThrows(com.fss.backend.common.ApiException.class,
                () -> service.activateImages("<img src=\"/files/2026-05-06/a.png\">"));
    }
}
