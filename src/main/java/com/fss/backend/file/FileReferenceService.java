package com.fss.backend.file;

import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileReferenceService {
    private final FileAssetRepository fileAssetRepository;
    private final EcommerceSupport support;
    private final HtmlContentImageUsageService htmlContentImageUsageService;

    public FileReferenceService(FileAssetRepository fileAssetRepository,
                                EcommerceSupport support,
                                HtmlContentImageUsageService htmlContentImageUsageService) {
        this.fileAssetRepository = fileAssetRepository;
        this.support = support;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
    }

    public void releaseFile(UUID fileId) {
        if (fileId == null) {
            return;
        }
        FileAsset file = fileAssetRepository.findById(fileId);
        if (file == null) {
            return;
        }
        deactivateIfUnused(file);
    }

    public void releaseFiles(Collection<UUID> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        fileIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::releaseFile);
    }

    public void releaseRemovedHtmlImages(String previousHtml, String currentHtml) {
        Set<String> previousPaths = new HashSet<>(htmlContentImageUsageService.extractFilePaths(previousHtml));
        if (previousPaths.isEmpty()) {
            return;
        }
        previousPaths.removeAll(htmlContentImageUsageService.extractFilePaths(currentHtml));
        for (String path : previousPaths) {
            FileAsset file = fileAssetRepository.findByPath(path);
            if (file != null) {
                deactivateIfUnused(file);
            }
        }
    }

    private void deactivateIfUnused(FileAsset file) {
        if (fileAssetRepository.countActiveReferences(file.id(), file.path()) == 0) {
            fileAssetRepository.updateStatus(file.id(), EcommerceSupport.INACTIVE, support.adminId());
        }
    }
}
