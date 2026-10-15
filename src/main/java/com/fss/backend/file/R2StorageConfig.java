package com.fss.backend.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
public class R2StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client r2Client(@Value("${app.storage.r2.endpoint}") String endpoint,
                      @Value("${app.storage.r2.access-key-id}") String accessKeyId,
                      @Value("${app.storage.r2.secret-access-key}") String secretAccessKey) {
        require(endpoint, "R2_ENDPOINT");
        require(accessKeyId, "R2_ACCESS_KEY_ID");
        require(secretAccessKey, "R2_SECRET_ACCESS_KEY");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint.trim()))
                // R2 ignores the region but the SDK requires one; Cloudflare documents "auto".
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        // Chunked (aws-chunked) uploads are not supported by R2.
                        .chunkedEncodingEnabled(false)
                        .build())
                // Newer SDKs add CRC checksums by default; only send/validate them when an operation requires it.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .build();
    }

    @Bean
    FileStorage r2FileStorage(S3Client r2Client, @Value("${app.storage.r2.bucket}") String bucket) {
        require(bucket, "R2_BUCKET");
        // Keys are "files/<path>" so that public URLs keep the same "/files/<path>" shape as the old local storage;
        // HTML content and the reference tracking rely on it.
        return new R2FileStorage(r2Client, bucket.trim(), FileUrlService.FILES_PREFIX);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when STORAGE_PROVIDER=r2");
        }
    }
}
