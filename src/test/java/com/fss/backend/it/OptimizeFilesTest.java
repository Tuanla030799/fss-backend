package com.fss.backend.it;

import com.fss.backend.support.TestImages;
import com.fss.migration.MigrationCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** migration-tool optimize-files: legacy uploads are optimized into a new directory with identical relative paths. */
class OptimizeFilesTest {

    @Test
    void optimizesLegacyUploadsKeepingPathsAndOriginals(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("uploads");
        Path target = temp.resolve("optimized");
        Files.createDirectories(source.resolve("2026-06-01"));
        byte[] photo = TestImages.withExifOrientation(TestImages.jpeg(4000, 2000), 6);
        byte[] smallPng = TestImages.png(40, 30, false);
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0, 0};
        Files.write(source.resolve("2026-06-01/aaa-IMG 1.JPG"), photo);
        Files.write(source.resolve("2026-06-01/bbb-logo.png"), smallPng);
        Files.write(source.resolve("2026-06-01/ccc-anim.gif"), gif);
        Files.write(source.resolve("2026-06-01/ddd-iphone.heic"), TestImages.heicHeader());

        assertEquals(0, MigrationCli.run(new String[]{"optimize-files", "--source-dir", source.toString(),
                "--target-dir", target.toString(), "--dry-run"}));
        assertTrue(Files.notExists(target), "dry run writes nothing");

        assertEquals(0, MigrationCli.run(new String[]{"optimize-files", "--source-dir", source.toString(),
                "--target-dir", target.toString()}));

        byte[] optimized = Files.readAllBytes(target.resolve("2026-06-01/aaa-IMG 1.JPG"));
        BufferedImage image = TestImages.read(optimized);
        assertEquals(800, image.getWidth(), "rotated to portrait and fitted in 1600x1600");
        assertEquals(1600, image.getHeight());
        assertTrue(optimized.length < photo.length);
        assertTrue(TestImages.read(Files.readAllBytes(target.resolve("2026-06-01/bbb-logo.png"))).getWidth() == 40);
        assertArrayEquals(gif, Files.readAllBytes(target.resolve("2026-06-01/ccc-anim.gif")));
        assertArrayEquals(TestImages.heicHeader(), Files.readAllBytes(target.resolve("2026-06-01/ddd-iphone.heic")));
        assertArrayEquals(photo, Files.readAllBytes(source.resolve("2026-06-01/aaa-IMG 1.JPG")), "source untouched");
    }
}
