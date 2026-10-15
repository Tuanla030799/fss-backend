package com.fss.backend.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Builds public file URLs from configuration plus the stored relative path, so no hostname is persisted with files.
 *
 * <p>Resolution order for the base URL: {@code app.cdn.base-url} (APP_CDN_BASE_URL) when set; otherwise, with
 * {@code STORAGE_PROVIDER=r2}, {@code R2_PUBLIC_BASE_URL + "/files"}; otherwise {@code /files} (served by this app).
 */
@Service
public class FileUrlService {
    private static final Logger log = LoggerFactory.getLogger(FileUrlService.class);
    public static final String FILES_PREFIX = "files/";

    private final String cdnBaseUrl;

    public FileUrlService(String cdnBaseUrl) {
        this.cdnBaseUrl = normalizeBaseUrl(cdnBaseUrl);
    }

    @Autowired
    public FileUrlService(@Value("${app.cdn.base-url:}") String cdnBaseUrl,
                          @Value("${app.storage.provider:local}") String storageProvider,
                          @Value("${app.storage.r2.public-base-url:}") String r2PublicBaseUrl) {
        this(resolveBaseUrl(cdnBaseUrl, storageProvider, r2PublicBaseUrl));
        if (!this.cdnBaseUrl.endsWith("/files")) {
            log.warn("Public file base URL {} does not end with /files; HTML image tracking expects /files/<path> URLs", this.cdnBaseUrl);
        }
        log.info("Public file base URL={}", this.cdnBaseUrl);
    }

    public String publicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return cdnBaseUrl + "/" + UriUtils.encodePath(relativePath, StandardCharsets.UTF_8);
    }

    public String baseUrl() {
        return cdnBaseUrl;
    }

    static String resolveBaseUrl(String cdnBaseUrl, String storageProvider, String r2PublicBaseUrl) {
        if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
            return cdnBaseUrl;
        }
        if ("r2".equalsIgnoreCase(storageProvider)) {
            if (r2PublicBaseUrl == null || r2PublicBaseUrl.isBlank()) {
                throw new IllegalStateException("R2_PUBLIC_BASE_URL (or APP_CDN_BASE_URL) must be set when STORAGE_PROVIDER=r2");
            }
            return normalizeBaseUrl(r2PublicBaseUrl) + "/files";
        }
        return "/files";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = (baseUrl == null || baseUrl.isBlank()) ? "/files" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
