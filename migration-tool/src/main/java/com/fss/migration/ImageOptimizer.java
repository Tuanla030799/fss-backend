package com.fss.migration;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Optimizes legacy uploads before they are synced to R2, applying the same rules as the API upload pipeline:
 * keep aspect ratio, never upscale, fit in max x max, apply EXIF orientation, drop metadata (EXIF/GPS).
 *
 * <p>The relative path and the file extension are kept unchanged, so {@code files.path}, product/brand references and
 * {@code /files/...} links in HTML stay valid. Source files are never modified: results are written to a separate
 * directory. JPEG/PNG are re-encoded in the same format; when a file needs no resize/rotation, has no EXIF and
 * re-encoding would make it bigger, the original is copied as-is. GIF, WebP (no pure-Java encoder), HEIC and anything unreadable are
 * copied unchanged and listed in the report.
 */
final class ImageOptimizer {
    private final Path source;
    private final Path target;
    private final int max;
    private final float quality;
    private final boolean dryRun;

    ImageOptimizer(Path source, Path target, int max, float quality, boolean dryRun) {
        this.source = source.toAbsolutePath().normalize();
        this.target = target.toAbsolutePath().normalize();
        this.max = max;
        this.quality = quality;
        this.dryRun = dryRun;
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    int run() throws IOException {
        if (target.startsWith(source) || source.startsWith(target)) {
            throw new IllegalArgumentException("--target-dir must be outside --source-dir");
        }
        long before = 0, after = 0;
        int optimized = 0, copied = 0, failed = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(source)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            Path out = target.resolve(source.relativize(file).toString());
            byte[] original = Files.readAllBytes(file);
            before += original.length;
            byte[] result;
            String note;
            try {
                result = optimize(original, extension(file));
                note = result == null ? "copied (format kept as-is)" : null;
            } catch (Exception ex) {
                result = null;
                note = "copied (cannot decode: " + ex.getMessage() + ")";
                failed++;
            }
            if (result == null) {
                result = original;
                copied++;
                System.out.println("  " + source.relativize(file) + ": " + note);
            } else {
                optimized++;
            }
            after += result.length;
            if (!dryRun) {
                Files.createDirectories(out.getParent());
                Path temp = Files.createTempFile(out.getParent(), ".opt-", ".tmp");
                Files.write(temp, result);
                Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        }
        System.out.printf("files=%d optimized=%d copiedUnchanged=%d decodeErrors=%d size %.1f MB -> %.1f MB%s%n",
                files.size(), optimized, copied, failed, before / 1048576.0, after / 1048576.0, dryRun ? " (dry run)" : "");
        return 0;
    }

    /** Returns the optimized bytes, or null when the file should be copied unchanged. */
    byte[] optimize(byte[] bytes, String extension) throws IOException {
        boolean jpeg = isJpeg(bytes);
        boolean png = isPng(bytes);
        // Keep the stored format so the extension in the path stays truthful.
        if (!(jpeg && (extension.equals("jpg") || extension.equals("jpeg"))) && !(png && extension.equals("png"))) {
            return null;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int orientation = jpeg ? orientation(bytes) : 1;
                boolean swap = orientation >= 5;
                int dw = swap ? height : width;
                int dh = swap ? width : height;
                double ratio = Math.min(1.0, Math.min((double) max / dw, (double) max / dh));
                int tw = Math.max(1, (int) Math.round(dw * ratio));
                int th = Math.max(1, (int) Math.round(dh * ratio));
                boolean changesPixels = ratio < 1.0 || orientation > 1;
                ImageReadParam param = reader.getDefaultReadParam();
                int sub = Math.max(1, (int) Math.floor(1.0 / ratio));
                if (sub > 1) param.setSourceSubsampling(sub, sub, 0, 0);
                BufferedImage decoded = reader.read(0, param);
                boolean alpha = png && decoded.getColorModel().hasAlpha();
                BufferedImage scaled = scale(decoded, swap ? th : tw, swap ? tw : th, alpha);
                BufferedImage oriented = orient(scaled, orientation, alpha);
                byte[] encoded = jpeg ? writeJpeg(oriented) : writePng(oriented);
                if (!changesPixels && encoded.length >= bytes.length && !(jpeg && hasExif(bytes))) {
                    // Nothing to fix and re-encoding would only make it bigger: keep the original bytes.
                    return null;
                }
                return encoded;
            } finally {
                reader.dispose();
            }
        }
    }

    /** True when the JPEG carries an EXIF block (may contain GPS); such files are always re-encoded without it. */
    static boolean hasExif(byte[] b) {
        int limit = Math.min(b.length - 10, 65536);
        for (int i = 2; i < limit; i++) {
            if ((b[i] & 0xFF) == 0xFF && (b[i + 1] & 0xFF) == 0xE1 && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i'
                    && b[i + 7] == 'f') {
                return true;
            }
        }
        return false;
    }

    private static int orientation(byte[] bytes) {
        try {
            var dir = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes), bytes.length)
                    .getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int value = dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return value >= 1 && value <= 8 ? value : 1;
            }
        } catch (Exception ignored) {
            // no usable EXIF
        }
        return 1;
    }

    private static BufferedImage scale(BufferedImage source, int w, int h, boolean alpha) {
        BufferedImage target = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        if (!alpha) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return target;
    }

    private static BufferedImage orient(BufferedImage source, int orientation, boolean alpha) {
        if (orientation <= 1 || orientation > 8) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.drawImage(source, t, null);
        g.dispose();
        return target;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
