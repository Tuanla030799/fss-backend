package com.fss.backend.file;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real {@link R2StorageConfig} client against a local fake S3 endpoint and checks the requests it sends:
 * path-style URLs, plain (not aws-chunked) bodies, no optional CRC checksum headers, SigV4 auth, content/cache headers.
 * This verifies the client configuration Cloudflare R2 needs; it does not replace a smoke test against a real bucket.
 */
class R2FileStorageTest {
    private HttpServer server;
    private final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger deleteStatus = new AtomicInteger(204);
    private S3Client client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(Map.of("method", exchange.getRequestMethod(), "path", exchange.getRequestURI().getRawPath(),
                    "headers", exchange.getRequestHeaders(), "body", body));
            int status = "DELETE".equals(exchange.getRequestMethod()) ? deleteStatus.get() : 200;
            if ("PUT".equals(exchange.getRequestMethod())) exchange.getResponseHeaders().add("ETag", "\"abc\"");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        client = new R2StorageConfig().r2Client("http://127.0.0.1:" + server.getAddress().getPort(), "key-id", "secret");
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void putUsesPathStyleSingleChunkUploadWithMetadata() throws Exception {
        R2FileStorage storage = new R2FileStorage(client, "shoes", FileUrlService.FILES_PREFIX);
        String key = storage.objectKey("2026-10-12/abc-a.jpg");
        assertEquals("files/2026-10-12/abc-a.jpg", key);

        byte[] bytes = {1, 2, 3, 4};
        storage.put(key, bytes, "image/jpeg");

        Map<String, Object> request = requests.get(0);
        var headers = (com.sun.net.httpserver.Headers) request.get("headers");
        assertEquals("PUT", request.get("method"));
        assertEquals("/shoes/files/2026-10-12/abc-a.jpg", request.get("path"));
        assertArrayEquals(bytes, (byte[]) request.get("body"));
        assertEquals("image/jpeg", headers.getFirst("Content-Type"));
        assertEquals(R2FileStorage.CACHE_CONTROL, headers.getFirst("Cache-Control"));
        assertTrue(headers.getFirst("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=key-id/"));
        assertTrue(headers.getFirst("Authorization").contains("/auto/s3/aws4_request"));
        assertNull(headers.getFirst("Content-Encoding"), "aws-chunked encoding is not supported by R2");
        assertFalse(headers.keySet().stream().anyMatch(h -> h.toLowerCase().startsWith("x-amz-checksum-")),
                "optional CRC checksums must not be sent: " + headers.keySet());
    }

    @Test
    void deleteIsIdempotentAndErrorsBecomeIOException() throws Exception {
        R2FileStorage storage = new R2FileStorage(client, "shoes", "files/");
        storage.delete("files/a.png");
        assertEquals("DELETE", requests.get(0).get("method"));
        assertEquals("/shoes/files/a.png", requests.get(0).get("path"));

        deleteStatus.set(503);
        assertThrows(IOException.class, () -> storage.delete("files/b.png"));
    }

    @Test
    void urlServiceDerivesPublicBaseFromR2Domain() {
        assertEquals("https://img.example.com/files",
                FileUrlService.resolveBaseUrl("", "r2", "https://img.example.com/"));
        assertEquals("https://cdn.example.com/files",
                FileUrlService.resolveBaseUrl("https://cdn.example.com/files", "r2", "https://img.example.com"));
        assertEquals("/files", FileUrlService.resolveBaseUrl(null, "local", null));
        assertThrows(IllegalStateException.class, () -> FileUrlService.resolveBaseUrl("", "r2", ""));
        assertNull(new FileUrlService("/files").publicUrl(null));
    }
}
