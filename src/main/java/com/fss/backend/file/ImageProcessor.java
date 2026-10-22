package com.fss.backend.file;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Validates and normalizes uploaded images with pure-Java codecs (JDK ImageIO + TwelveMonkeys), so no native
 * library is needed on Heroku.
 *
 * <ul>
 *   <li>The real format is detected from the file bytes; client MIME type and extension are ignored.</li>
 *   <li>Dimensions are read from the header and limited by total pixels before any pixel data is decoded.</li>
 *   <li>Large images are decoded with source subsampling, so the decoded raster stays close to the target size
 *       instead of the full camera resolution.</li>
 *   <li>Images keep their aspect ratio, are never upscaled, are rotated according to EXIF orientation and are
 *       re-encoded without metadata (EXIF/GPS is dropped).</li>
 *   <li>A semaphore bounds concurrent decodes, because one decode can use tens of MB regardless of file size.</li>
 * </ul>
 */
@Component
public class ImageProcessor {
    private static final Logger log = LoggerFactory.getLogger(ImageProcessor.class);

    public enum Format {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), GIF("image/gif", "gif"), WEBP("image/webp", "webp"),
        HEIF("image/heif", "heic"), UNKNOWN("application/octet-stream", "bin");

        final String contentType;
        final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    public record Result(byte[] bytes, String contentType, String extension, boolean resized, Integer width, Integer height) {}

    private final int maxWidth;
    private final int maxHeight;
    private final long maxPixels;
    private final float jpegQuality;
    private final Semaphore permits;
    private final long acquireTimeoutMs;

    public ImageProcessor(@Value("${app.image.resize.max-width:1600}") int maxWidth,
                          @Value("${app.image.resize.max-height:1600}") int maxHeight,
                          @Value("${app.image.max-pixels:40000000}") long maxPixels,
                          @Value("${app.image.jpeg-quality:0.85}") float jpegQuality,
                          @Value("${app.image.max-concurrent:2}") int maxConcurrent,
                          @Value("${app.image.acquire-timeout-ms:20000}") long acquireTimeoutMs) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxPixels = maxPixels;
        this.jpegQuality = Math.min(1f, Math.max(0.1f, jpegQuality));
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    @PostConstruct
    void registerPlugins() {
        // Register TwelveMonkeys readers from the Spring Boot nested jar class loader.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    public static Format detect(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return Format.PNG;
        }
        if (bytes.length >= 6 && ascii(bytes, 0, 4).equals("GIF8")) {
            return Format.GIF;
        }
        if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return Format.WEBP;
        }
        if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
            String brand = ascii(bytes, 8, 4);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")
                    || brand.equals("avif") || brand.equals("avis")) {
                return Format.HEIF;
            }
        }
        return Format.UNKNOWN;
    }

    public Result process(byte[] bytes) throws IOException {
        Format format = detect(bytes);
        if (format == Format.HEIF) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "HEIC/HEIF/AVIF images are not supported; please convert to JPEG or PNG before uploading");
        }
        if (format == Format.UNKNOWN) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "Unsupported file type; only JPEG, PNG, GIF or WebP images are allowed");
        }
        acquire();
        try {
            return doProcess(bytes, format);
        } finally {
            permits.release();
        }
    }

    private Result doProcess(byte[] bytes, Format format) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = readerFor(input, format);
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new ApiException(ErrorCode.IMAGE_TOO_LARGE, "Image dimensions are too large (max " + maxPixels + " pixels)");
                }
                if (format == Format.GIF) {
                    // Keep GIFs byte-for-byte so animations survive; they carry no EXIF/GPS data.
                    return new Result(bytes, format.contentType, format.extension, false, width, height);
                }

                int orientation = format == Format.PNG ? 1 : exifOrientation(bytes);
                boolean swap = orientation >= 5 && orientation <= 8;
                int displayWidth = swap ? height : width;
                int displayHeight = swap ? width : height;
                double ratio = Math.min(1.0d, Math.min((double) maxWidth / displayWidth, (double) maxHeight / displayHeight));
                int targetWidth = Math.max(1, (int) Math.round(displayWidth * ratio));
                int targetHeight = Math.max(1, (int) Math.round(displayHeight * ratio));
                int subsampling = Math.max(1, (int) Math.floor(1.0d / ratio));

                ImageReadParam param = reader.getDefaultReadParam();
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage decoded = reader.read(0, param);
                boolean alpha = decoded.getColorModel().hasAlpha() && format != Format.JPEG;
                int storedWidth = swap ? targetHeight : targetWidth;
                int storedHeight = swap ? targetWidth : targetHeight;
                BufferedImage scaled = scale(decoded, storedWidth, storedHeight, alpha);
                decoded.flush();
                BufferedImage oriented = orient(scaled, orientation, alpha);

                Format output = format == Format.WEBP ? (alpha ? Format.PNG : Format.JPEG) : format;
                byte[] encoded = output == Format.JPEG ? writeJpeg(oriented) : writePng(oriented);
                boolean resized = targetWidth != displayWidth || targetHeight != displayHeight;
                log.debug("Image processed format={} output={} source={}x{} target={}x{} subsampling={} orientation={}",
                        format, output, width, height, targetWidth, targetHeight, subsampling, orientation);
                return new Result(encoded, output.contentType, output.extension, resized, targetWidth, targetHeight);
            } finally {
                reader.dispose();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("Image decode failed format={} cause={}", format, ex.toString());
            throw new ApiException(ErrorCode.IMAGE_CORRUPTED, "Image file is corrupted or cannot be processed");
        }
    }

    private ImageReader readerFor(ImageInputStream input, Format format) {
        if (input == null) throw new ApiException(ErrorCode.IMAGE_CORRUPTED, "Image file is corrupted or cannot be processed");
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            readers = ImageIO.getImageReadersByMIMEType(format.contentType);
        }
        if (!readers.hasNext()) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_SUPPORTED, "No decoder available for " + format.name());
        }
        return readers.next();
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVER_BUSY, "Server is busy processing images, please retry");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVER_BUSY, "Server is busy processing images, please retry");
        }
    }

    static int exifOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes), bytes.length);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int value = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return value >= 1 && value <= 8 ? value : 1;
            }
        } catch (Exception ignored) {
            // Missing or malformed metadata: keep the image as stored.
        }
        return 1;
    }

    private static BufferedImage scale(BufferedImage source, int width, int height, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getWidth() == width && source.getHeight() == height && source.getType() == type) {
            return source;
        }
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            if (!alpha) {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Applies EXIF orientation 2..8 so the stored pixels are upright and no orientation tag is needed. */
    static BufferedImage orient(BufferedImage source, int orientation, boolean alpha) {
        if (orientation <= 1 || orientation > 8) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00*x + m01*y + m02, y' = m10*x + m11*y + m12.
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotate 180
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirror vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90 clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: rotate 90 counter-clockwise
        };
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(source, t, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(jpegQuality);
            if (param.canWriteProgressive()) {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            // No metadata argument: EXIF, GPS and comments are not carried over.
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("No PNG writer available");
        }
        return output.toByteArray();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
