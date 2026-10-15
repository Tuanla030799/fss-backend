package com.fss.backend.file;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * With R2 storage the API no longer serves bytes. Old links of the form {@code <api-host>/files/<path>} (stored in
 * HTML or bookmarked) are permanently redirected to the public R2 URL of the same object.
 */
@RestController
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
public class LegacyFileRedirectController {
    private final FileUrlService fileUrlService;

    public LegacyFileRedirectController(FileUrlService fileUrlService) {
        this.fileUrlService = fileUrlService;
    }

    @GetMapping("/files/**")
    public ResponseEntity<Void> redirect(HttpServletRequest request) {
        String path = UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length() + "/files/".length()),
                StandardCharsets.UTF_8);
        if (path.isBlank() || path.contains("..") || fileUrlService.baseUrl().startsWith("/")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, fileUrlService.publicUrl(path))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .build();
    }
}
