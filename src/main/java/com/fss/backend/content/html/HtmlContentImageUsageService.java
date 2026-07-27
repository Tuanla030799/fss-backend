package com.fss.backend.content.html;

import com.fss.backend.file.FileAsset;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class HtmlContentImageUsageService {
    private final FileAssetRepository fileAssetRepository;
    private final EcommerceSupport support;

    public HtmlContentImageUsageService(FileAssetRepository fileAssetRepository, EcommerceSupport support) {
        this.fileAssetRepository = fileAssetRepository;
        this.support = support;
    }

    public void activateImages(String sanitizedHtml) {
        extractFilePaths(sanitizedHtml).forEach(this::activateByPath);
    }

    public Set<String> extractFilePaths(String html) {
        if (html == null || html.isBlank()) {
            return Set.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        Jsoup.parseBodyFragment(html).select("img[src]").forEach(image -> {
            String path = toFilePath(image.attr("src"));
            if (path != null && !path.isBlank()) {
                paths.add(path);
            }
        });
        return paths;
    }

    private void activateByPath(String path) {
        FileAsset file = fileAssetRepository.findByPath(path);
        if (file != null) {
            fileAssetRepository.updateStatus(file.id(), EcommerceSupport.ACTIVE, support.adminId());
        }
    }

    private String toFilePath(String src) {
        return HtmlFilePathUtils.localFilePath(src);
    }
}
