package com.fss.backend.file;

import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final FileAssetRepository repository;
    private final FileStorage storage;
    private final CurrentAdmin currentAdmin;
    private final FileUrlService fileUrlService;
    private final int resizeMaxWidth;
    private final int resizeMaxHeight;
    private final long resizeMinBytes;

    public FileService(FileAssetRepository repository, FileStorage storage, CurrentAdmin currentAdmin,
                       FileUrlService fileUrlService,
                       @Value("${app.image.resize.max-width:1600}") int resizeMaxWidth,
                       @Value("${app.image.resize.max-height:1600}") int resizeMaxHeight,
                       @Value("${app.image.resize.min-bytes:512000}") long resizeMinBytes) {
        this.repository = repository;
        this.storage = storage;
        this.currentAdmin = currentAdmin;
        this.fileUrlService = fileUrlService;
        this.resizeMaxWidth = resizeMaxWidth;
        this.resizeMaxHeight = resizeMaxHeight;
        this.resizeMinBytes = resizeMinBytes;
    }

    @Transactional
    public Map<String, String> upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException("File is empty");
        }

        UploadPayload payload = prepareUploadPayload(file);
        String relativePath = storage.saveBytes(payload.filename(), payload.bytes());
        UUID id = UUID.randomUUID();
        repository.create(id, relativePath, STATUS_INACTIVE, currentAdmin.idOrNull());
        log.info("File uploaded fileId={} path={} status={} resized={}", id, relativePath, STATUS_INACTIVE, payload.resized());

        Map<String, String> data = new HashMap<>();
        data.put("fileId", id.toString());
        data.put("path", relativePath);
        data.put("url", fileUrlService.publicUrl(relativePath));
        data.put("resized", Boolean.toString(payload.resized()));
        if (payload.width() != null) data.put("width", payload.width().toString());
        if (payload.height() != null) data.put("height", payload.height().toString());
        return data;
    }

    private UploadPayload prepareUploadPayload(MultipartFile file) throws IOException {
        byte[] originalBytes = file.getBytes();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            return new UploadPayload(file.getOriginalFilename(), originalBytes, false, null, null);
        }

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (source == null) {
            return new UploadPayload(file.getOriginalFilename(), originalBytes, false, null, null);
        }

        boolean tooLargeDimension = source.getWidth() > resizeMaxWidth || source.getHeight() > resizeMaxHeight;
        boolean tooLargeFile = originalBytes.length >= resizeMinBytes;
        if (!tooLargeDimension && !tooLargeFile) {
            return new UploadPayload(file.getOriginalFilename(), originalBytes, false, source.getWidth(), source.getHeight());
        }

        double ratio = Math.min((double) resizeMaxWidth / source.getWidth(), (double) resizeMaxHeight / source.getHeight());
        ratio = Math.min(ratio, 1.0d);
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));

        String format = contentType.contains("png") ? "png" : "jpg";
        int imageType = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean ok = ImageIO.write(resized, format, output);
        if (!ok) {
            return new UploadPayload(file.getOriginalFilename(), originalBytes, false, source.getWidth(), source.getHeight());
        }
        String filename = normalizeResizedFilename(file.getOriginalFilename(), format);
        return new UploadPayload(filename, output.toByteArray(), true, targetWidth, targetHeight);
    }

    private String normalizeResizedFilename(String originalName, String format) {
        String name = originalName == null || originalName.isBlank() ? "image" : originalName;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "." + format;
    }

    @Scheduled(fixedDelayString = "${app.file-cleanup.fixed-delay-ms:60000}")
    public void cleanupInactiveFiles() {
        cleanupInactiveFiles(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
    }

    @Transactional
    public void cleanupInactiveFiles(OffsetDateTime cutoff) {
        var files = repository.listInactiveCreatedBefore(cutoff);
        for (var file : files) {
            try {
                storage.delete(file.path());
            } catch (IOException ignored) {
                // Keep DB cleanup moving; missing local files should not block stale record removal.
            }
            repository.deleteById(file.id());
        }
    }

    private record UploadPayload(String filename, byte[] bytes, boolean resized, Integer width, Integer height) {}
}
