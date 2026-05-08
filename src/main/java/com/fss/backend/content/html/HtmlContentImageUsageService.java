package com.fss.backend.content.html;

import com.fss.backend.file.FileAsset;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class HtmlContentImageUsageService {
    private final FileAssetRepository fileAssetRepository;
    private final EcommerceSupport support;

    public HtmlContentImageUsageService(FileAssetRepository fileAssetRepository, EcommerceSupport support) {
        this.fileAssetRepository = fileAssetRepository;
        this.support = support;
    }

    public void activateImages(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return;
        }
        Jsoup.parseBodyFragment(sanitizedHtml).select("img[src]").stream()
                .map(image -> toFilePath(image.attr("src")))
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .forEach(this::activateByPath);
    }

    private void activateByPath(String path) {
        FileAsset file = fileAssetRepository.findByPath(path);
        if (file != null) {
            fileAssetRepository.updateStatus(file.id(), EcommerceSupport.ACTIVE, support.adminId());
        }
    }

    private String toFilePath(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        if (src.startsWith("/files/")) {
            return src.substring("/files/".length());
        }
        try {
            URI uri = URI.create(src);
            String path = uri.getPath();
            if (path != null && path.startsWith("/files/")) {
                return path.substring("/files/".length());
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }
}
