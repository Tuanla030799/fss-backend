package com.fss.backend.logging;

import com.fss.backend.common.ApiException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LogStreamService {
    private static final long EMITTER_TIMEOUT_MS = 0L;
    private static final long POLL_INTERVAL_MS = 1_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final Path currentLogFile;
    private final int maxInitialLines;
    private final int maxConnections;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool(new LogStreamThreadFactory());

    public LogStreamService(@Value("${logging.file.path:./logs}") String logPath,
                            @Value("${spring.application.name:fss-backend}") String appName,
                            @Value("${app.logs.stream.max-initial-lines:1000}") int maxInitialLines,
                            @Value("${app.logs.stream.max-connections:5}") int maxConnections) {
        this.currentLogFile = Path.of(logPath).resolve(appName + ".log").normalize();
        this.maxInitialLines = Math.max(1, maxInitialLines);
        this.maxConnections = Math.max(1, maxConnections);
    }

    public SseEmitter streamCurrentLog(int requestedLines) {
        if (activeConnections.incrementAndGet() > maxConnections) {
            activeConnections.decrementAndGet();
            throw new ApiException("Too many active log streams");
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(error -> open.set(false));

        executor.execute(() -> {
            try {
                stream(emitter, open, normalizeInitialLines(requestedLines));
            } finally {
                open.set(false);
                activeConnections.decrementAndGet();
            }
        });

        return emitter;
    }

    private int normalizeInitialLines(int requestedLines) {
        if (requestedLines <= 0) {
            return 0;
        }
        return Math.min(requestedLines, maxInitialLines);
    }

    private void stream(SseEmitter emitter, AtomicBoolean open, int initialLines) {
        try {
            send(emitter, "ready", currentLogFile.getFileName().toString());
            for (String line : tailLines(initialLines)) {
                send(emitter, "log", line);
            }
            follow(emitter, open);
        } catch (Exception ex) {
            if (open.get()) {
                emitter.completeWithError(ex);
            }
        }
    }

    private List<String> tailLines(int maxLines) throws IOException {
        if (maxLines <= 0 || !Files.exists(currentLogFile)) {
            return List.of();
        }

        ArrayDeque<String> lines = new ArrayDeque<>(maxLines);
        try (BufferedReader reader = Files.newBufferedReader(currentLogFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == maxLines) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        }
        return new ArrayList<>(lines);
    }

    private void follow(SseEmitter emitter, AtomicBoolean open) throws IOException, InterruptedException {
        long position = Files.exists(currentLogFile) ? Files.size(currentLogFile) : 0L;
        long lastHeartbeatAt = System.currentTimeMillis();
        String pending = "";

        while (open.get()) {
            if (Files.exists(currentLogFile)) {
                long size = Files.size(currentLogFile);
                if (size < position) {
                    position = 0L;
                    pending = "";
                }
                if (size > position) {
                    byte[] bytes = readBytes(position, size);
                    position = size;
                    pending = sendCompleteLines(emitter, pending + new String(bytes, StandardCharsets.UTF_8));
                }
            }

            long now = System.currentTimeMillis();
            if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
                send(emitter, "heartbeat", Instant.now().toString());
                lastHeartbeatAt = now;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        emitter.complete();
    }

    private byte[] readBytes(long start, long end) throws IOException {
        try (var input = Files.newInputStream(currentLogFile)) {
            input.skipNBytes(start);
            return input.readNBytes(Math.toIntExact(end - start));
        }
    }

    private String sendCompleteLines(SseEmitter emitter, String content) throws IOException {
        int start = 0;
        int newline;
        while ((newline = content.indexOf('\n', start)) >= 0) {
            String line = content.substring(start, newline);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            send(emitter, "log", line);
            start = newline + 1;
        }
        return content.substring(start);
    }

    private void send(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static class LogStreamThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "log-stream-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
