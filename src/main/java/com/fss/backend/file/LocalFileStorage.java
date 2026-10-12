package com.fss.backend.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local-disk storage for development (and the legacy VM deployment). Never use it on Heroku: the dyno filesystem
 * is ephemeral and is wiped on every restart/deploy.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path uploadRoot;

    public LocalFileStorage(@Value("${app.upload-dir}") String uploadDir,
                            @Value("${app.storage.allow-local:true}") boolean allowLocal) {
        if (!allowLocal) {
            throw new IllegalStateException("STORAGE_PROVIDER=local is not allowed in this environment (ephemeral disk); use r2");
        }
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        log.info("File storage provider=local root={}", this.uploadRoot);
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public String objectKey(String relativePath) {
        return relativePath;
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        log.info("File stored provider=local key={} size={}", objectKey, bytes.length);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    private Path resolve(String objectKey) throws IOException {
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("Invalid object key");
        }
        return target;
    }
}
