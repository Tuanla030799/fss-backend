package com.fss.backend.content.html;

import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.file.FileAsset;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.file.FileUrlService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
        when(repository.findByPath("2026-05-06/a.png"))
                .thenReturn(new FileAsset(fileId, "2026-05-06/a.png", "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<p>x</p><img src=\"/files/2026-05-06/a.png\">");

        verify(repository).updateStatus(eq(fileId), eq(EcommerceSupport.ACTIVE), eq(adminId));
    }

    @Test
    void activateImagesShouldMarkAbsoluteLocalUrlsWithSpacesActive() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
        UUID adminId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String path = "2026-07-27/c60b717b-Screenshot from 2026-07-23.png";
        when(currentAdmin.idOrNull()).thenReturn(adminId);
        when(repository.findByPath(path))
                .thenReturn(new FileAsset(fileId, path, "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<img src=\"http://localhost:8080/files/" + path + "\">");

        verify(repository).updateStatus(eq(fileId), eq(EcommerceSupport.ACTIVE), eq(adminId));
    }

    @Test
    void activateImagesShouldDecodeEncodedFilePaths() {
        FileAssetRepository repository = mock(FileAssetRepository.class);
        CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
        UUID adminId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String path = "2026-07-27/c60b717b-Screenshot from 2026-07-23.png";
        when(currentAdmin.idOrNull()).thenReturn(adminId);
        when(repository.findByPath(path))
                .thenReturn(new FileAsset(fileId, path, "INACTIVE", null));
        EcommerceSupport support = new EcommerceSupport(currentAdmin, repository, new FileUrlService("/files"));
        HtmlContentImageUsageService service = new HtmlContentImageUsageService(repository, support);

        service.activateImages("<img src=\"/files/2026-07-27/c60b717b-Screenshot%20from%202026-07-23.png\">");

        verify(repository).updateStatus(eq(fileId), eq(EcommerceSupport.ACTIVE), eq(adminId));
    }
}
