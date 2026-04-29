package com.fss.backend.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileUrlService {
    private final String cdnBaseUrl;

    public FileUrlService(@Value("${app.cdn.base-url:/files}") String cdnBaseUrl) {
        this.cdnBaseUrl = normalizeBaseUrl(cdnBaseUrl);
    }

    public String publicUrl(String relativePath) {
        return cdnBaseUrl + "/" + relativePath;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = (baseUrl == null || baseUrl.isBlank()) ? "/files" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
