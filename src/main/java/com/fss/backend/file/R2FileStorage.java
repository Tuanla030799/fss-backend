package com.fss.backend.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

/**
 * Cloudflare R2 storage through the S3-compatible API (not the Cloudflare Images product). Objects are public only
 * through the bucket's custom domain / r2.dev URL configured as {@code R2_PUBLIC_BASE_URL}; credentials stay on the
 * server.
 */
public class R2FileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(R2FileStorage.class);
    /** Uploaded names are unique (UUID prefix), so objects never change and can be cached for a year. */
    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String bucket;
    private final String keyPrefix;

    public R2FileStorage(S3Client client, String bucket, String keyPrefix) {
        this.client = client;
        this.bucket = bucket;
        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    @Override
    public String provider() {
        return "r2";
    }

    @Override
    public String objectKey(String relativePath) {
        return keyPrefix + relativePath;
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) throws IOException {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .cacheControl(CACHE_CONTROL)
                    .build(), RequestBody.fromBytes(bytes));
            log.info("File stored provider=r2 key={} size={}", objectKey, bytes.length);
        } catch (SdkException ex) {
            throw new IOException("R2 upload failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            // S3/R2 DeleteObject succeeds for missing keys, which makes retries idempotent.
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (SdkException ex) {
            throw new IOException("R2 delete failed: " + ex.getMessage(), ex);
        }
    }

    static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "";
        String value = prefix.trim();
        while (value.startsWith("/")) value = value.substring(1);
        return value.endsWith("/") ? value : value + "/";
    }
}
