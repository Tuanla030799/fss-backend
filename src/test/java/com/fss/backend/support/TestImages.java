package com.fss.backend.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TestImages {
    private TestImages() {
    }

    public static byte[] png(int width, int height, boolean alpha) {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(alpha ? new Color(255, 0, 0, 128) : Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return write(image, "png");
    }

    /** JPEG whose left half is black and right half white (to verify rotation). */
    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.WHITE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return write(image, "jpeg");
    }

    /** Inserts an APP1 EXIF segment with the given orientation right after the JPEG SOI marker. */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8); // header, IFD0 at offset 8
        tiff.putShort((short) 1);                                             // one entry
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);           // Orientation, SHORT, count 1
        tiff.putShort((short) orientation).putShort((short) 0);                // value (left aligned)
        tiff.putInt(0);                                                        // no next IFD
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + exifHeader.length + tiff.capacity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Minimal ISO-BMFF header as written by iPhones (HEIC). */
    public static byte[] heicHeader() {
        return new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0, 'm', 'i', 'f', '1', 'h', 'e', 'i', 'c'};
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] write(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
