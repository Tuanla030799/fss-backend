package com.fss.backend.content.html;

import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;
import com.fss.backend.file.FileAsset;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.file.FileUrlService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class HtmlContentImageUsageService {
    private final FileAssetRepository fileAssetRepository;
    private final EcommerceSupport support;
    private final FileUrlService fileUrlService;
    private final HtmlSanitizerService sanitizer;

    @Autowired
    public HtmlContentImageUsageService(FileAssetRepository fileAssetRepository, EcommerceSupport support,
                                        FileUrlService fileUrlService, HtmlSanitizerService sanitizer) {
        this.fileAssetRepository = fileAssetRepository;
        this.support = support;
        this.fileUrlService = fileUrlService;
        this.sanitizer = sanitizer;
    }

    public HtmlContentImageUsageService(FileAssetRepository fileAssetRepository, EcommerceSupport support) {
        this(fileAssetRepository, support, new FileUrlService("/files"), new HtmlSanitizerService("", "/files"));
    }

    public void activateImages(String sanitizedHtml) {
        extractFilePaths(sanitizedHtml).forEach(this::activateByPath);
    }

    /** Relative file paths of every {@code <img src=".../files/<path>">} in the HTML. Stored as {@code htmlFilePaths}. */
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

    public List<String> filePathList(String html) {
        return List.copyOf(extractFilePaths(html));
    }

    /**
     * Rewrites image sources that point at managed files to the current public base URL, so content saved while
     * files were served from {@code /files} or an old domain keeps working after moving to R2/another CDN host.
     */
    public String rewriteForOutput(String html) {
        if (html == null || html.isBlank() || !html.contains("<img")) {
            return html;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        boolean[] changed = {false};
        document.select("img[src]").forEach(image -> {
            String src = image.attr("src");
            String path = sanitizer.imagePath(src);
            if (path != null && !path.isBlank()) {
                String url = fileUrlService.publicUrl(path);
                if (url != null && !url.equals(src)) {
                    image.attr("src", url);
                    changed[0] = true;
                }
            }
        });
        return changed[0] ? document.body().html() : html;
    }

    private void activateByPath(String path) {
        FileAsset file = fileAssetRepository.findByPath(path);
        if (file != null && fileAssetRepository.activate(file.id(), support.adminId()) == FileAssetRepository.ActivationResult.UNAVAILABLE) {
            throw new ApiException(ErrorCode.FILE_UNAVAILABLE, "Image in content is no longer available, please re-upload: " + path);
        }
    }

    private String toFilePath(String src) {
        return HtmlFilePathUtils.localFilePath(src);
    }
}
