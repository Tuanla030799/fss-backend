package com.fss.backend.file;

import com.fss.backend.common.ApiException;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessorTest {
    private final ImageProcessor processor = processor(2);

    private static ImageProcessor processor(int concurrent) {
        ImageProcessor p = new ImageProcessor(1600, 1600, 40_000_000L, 0.85f, concurrent, 200);
        p.registerPlugins();
        return p;
    }

    @Test
    void detectsFormatFromBytesNotFromNameOrMime() {
        assertEquals(ImageProcessor.Format.PNG, ImageProcessor.detect(TestImages.png(2, 2, false)));
        assertEquals(ImageProcessor.Format.JPEG, ImageProcessor.detect(TestImages.jpeg(2, 2)));
        assertEquals(ImageProcessor.Format.HEIF, ImageProcessor.detect(TestImages.heicHeader()));
        assertEquals(ImageProcessor.Format.UNKNOWN, ImageProcessor.detect("<html>".getBytes()));
    }

    @Test
    void rejectsHeicWithClearMessage() {
        ApiException ex = assertThrows(ApiException.class, () -> processor.process(TestImages.heicHeader()));
        assertTrue(ex.getMessage().contains("HEIC"));
    }

    @Test
    void rejectsNonImagesEvenWithImageExtension() {
        assertThrows(ApiException.class, () -> processor.process("%PDF-1.7 fake".getBytes()));
    }

    @Test
    void rejectsCorruptedImage() {
        byte[] truncated = java.util.Arrays.copyOf(TestImages.jpeg(100, 100), 40);
        assertThrows(ApiException.class, () -> processor.process(truncated));
    }

    @Test
    void doesNotUpscaleSmallImages() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.png(300, 200, false));
        assertFalse(result.resized());
        assertEquals(300, result.width());
        assertEquals(200, result.height());
        assertEquals("image/png", result.contentType());
    }

    @Test
    void resizesLargeImagesKeepingAspectRatio() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.jpeg(4000, 2000));
        assertTrue(result.resized());
        assertEquals(1600, result.width());
        assertEquals(800, result.height());
        BufferedImage decoded = TestImages.read(result.bytes());
        assertEquals(1600, decoded.getWidth());
        assertEquals(800, decoded.getHeight());
        assertEquals("image/jpeg", result.contentType());
        assertEquals("jpg", result.extension());
    }

    @Test
    void keepsTransparencyForPng() throws Exception {
        ImageProcessor.Result result = processor.process(TestImages.png(2000, 2000, true));
        BufferedImage decoded = TestImages.read(result.bytes());
        assertTrue(decoded.getColorModel().hasAlpha());
        assertEquals(1600, decoded.getWidth());
    }

    @Test
    void appliesExifOrientationAndDropsMetadata() throws Exception {
        // 200x100 landscape stored with orientation 6 (rotate 90 clockwise) must come out as 100x200 portrait.
        byte[] jpeg = TestImages.withExifOrientation(TestImages.jpeg(200, 100), 6);
        assertEquals(6, ImageProcessor.exifOrientation(jpeg));

        ImageProcessor.Result result = processor.process(jpeg);

        BufferedImage decoded = TestImages.read(result.bytes());
        assertEquals(100, decoded.getWidth());
        assertEquals(200, decoded.getHeight());
        // Left half (black) of the source becomes the top after a clockwise rotation.
        assertTrue(new Color(decoded.getRGB(50, 20)).getRed() < 60);
        assertTrue(new Color(decoded.getRGB(50, 180)).getRed() > 200);
        assertEquals(1, ImageProcessor.exifOrientation(result.bytes()));
        assertNotEquals(jpeg.length, result.bytes().length);
    }

    @Test
    void rejectsImagesAboveThePixelLimitBeforeDecoding() {
        ImageProcessor small = new ImageProcessor(1600, 1600, 10_000L, 0.85f, 1, 200);
        small.registerPlugins();
        ApiException ex = assertThrows(ApiException.class, () -> small.process(TestImages.png(200, 200, false)));
        assertTrue(ex.getMessage().contains("too large"));
    }

    @Test
    void limitsConcurrentProcessing() throws Exception {
        ImageProcessor single = processor(1);
        byte[] big = TestImages.jpeg(6000, 4000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger busy = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    single.process(big);
                    ok.incrementAndGet();
                } catch (ApiException ex) {
                    if (ex.status().value() == 503) busy.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertTrue(ok.get() >= 1);
        assertTrue(busy.get() >= 1, "with 1 permit and a 200 ms wait some requests must be rejected as busy");
    }
}
