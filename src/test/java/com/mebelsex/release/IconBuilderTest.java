package com.mebelsex.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the .ico / window-PNG builder (pure image work, no JavaFX). */
class IconBuilderTest {

    @TempDir
    Path temp;

    @Test
    void writesAnIcoWithSevenImagesAndAValidDirectory() throws Exception {
        Path source = writeSourcePng(300);
        Path ico = temp.resolve("out").resolve("zakazpro.ico");

        IconBuilder.Result result = IconBuilder.build(source, ico, temp.resolve("icons"));

        assertEquals(300, result.sourceWidth());
        assertEquals(300, result.sourceHeight());
        assertFalse(result.upscaled());
        assertTrue(result.warnings().isEmpty(), () -> "unexpected warnings: " + result.warnings());

        byte[] bytes = Files.readAllBytes(ico);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, buffer.getShort(0), "reserved must be 0");
        assertEquals(1, buffer.getShort(2), "type must be 1 (icon)");
        assertEquals(IconBuilder.ICO_SIZES.length, buffer.getShort(4), "one entry per size");

        for (int i = 0; i < IconBuilder.ICO_SIZES.length; i++) {
            int size = IconBuilder.ICO_SIZES[i];
            int base = 6 + i * 16;
            int expected = size >= 256 ? 0 : size;
            assertEquals(expected, bytes[base] & 0xff, "entry " + i + " width");
            assertEquals(expected, bytes[base + 1] & 0xff, "entry " + i + " height");
            assertEquals(0, bytes[base + 2] & 0xff, "entry " + i + " colour count");
            assertEquals(0, bytes[base + 3] & 0xff, "entry " + i + " reserved");
            assertEquals(1, buffer.getShort(base + 4), "entry " + i + " planes");
            assertEquals(32, buffer.getShort(base + 6), "entry " + i + " bit count");

            int entrySize = buffer.getInt(base + 8);
            int offset = buffer.getInt(base + 12);
            assertTrue(entrySize > 0, "entry " + i + " size");
            assertTrue(offset >= 6 + IconBuilder.ICO_SIZES.length * 16, "entry " + i + " past the directory");
            assertTrue(offset + entrySize <= bytes.length, "entry " + i + " stays inside the file");
        }
    }

    @Test
    void the256ImageIsPngAndTheSmallerOnesAreDibs() throws Exception {
        Path ico = temp.resolve("zakazpro.ico");
        IconBuilder.build(writeSourcePng(300), ico, temp.resolve("icons"));

        byte[] bytes = Files.readAllBytes(ico);
        for (int i = 0; i < IconBuilder.ICO_SIZES.length; i++) {
            int size = IconBuilder.ICO_SIZES[i];
            int offset = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(6 + i * 16 + 12);
            if (size >= 256) {
                assertTrue(IconBuilder.isPng(Arrays.copyOfRange(bytes, offset, offset + 8)),
                        "the 256 px image must be PNG");
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes, offset, bytes.length - offset));
                assertNotNull(decoded);
                assertEquals(256, decoded.getWidth());
                assertEquals(256, decoded.getHeight());
            } else {
                ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                assertEquals(40, header.getInt(offset), "BITMAPINFOHEADER size (entry " + size + ")");
                assertEquals(size, header.getInt(offset + 4), "biWidth");
                assertEquals(size * 2, header.getInt(offset + 8), "biHeight covers the AND mask");
                assertEquals(32, header.getShort(offset + 14), "biBitCount");
            }
        }
    }

    @Test
    void writesTheThreeWindowPngs() throws Exception {
        Path icons = temp.resolve("icons");
        IconBuilder.build(writeSourcePng(300), temp.resolve("zakazpro.ico"), icons);

        for (int size : IconBuilder.PNG_SIZES) {
            Path png = icons.resolve("app-" + size + ".png");
            assertTrue(Files.isRegularFile(png), png + " must exist");
            BufferedImage image = ImageIO.read(png.toFile());
            assertNotNull(image);
            assertEquals(size, image.getWidth());
            assertEquals(size, image.getHeight());
        }
    }

    @Test
    void nonSquareInputIsPaddedWithTransparentPixels() {
        BufferedImage wide = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 4; x++) {
                wide.setRGB(x, y, 0xffff0000);
            }
        }

        BufferedImage squared = IconBuilder.square(wide);

        assertEquals(4, squared.getWidth());
        assertEquals(4, squared.getHeight());
        assertEquals(0, squared.getRGB(0, 0) >>> 24, "top padding must be transparent");
        assertEquals(0, squared.getRGB(3, 3) >>> 24, "bottom padding must be transparent");
        assertEquals(0xffff0000, squared.getRGB(0, 1), "the artwork keeps its colour");
        assertEquals(0xffff0000, squared.getRGB(3, 2), "the artwork keeps its colour");
    }

    @Test
    void aNonSquareSourceIsReportedAsPadded() throws Exception {
        Path source = temp.resolve("wide.png");
        ImageIO.write(widePng(512, 256), "png", source.toFile());

        IconBuilder.Result result = IconBuilder.build(source, temp.resolve("zakazpro.ico"), temp.resolve("icons"));

        assertFalse(result.upscaled());
        assertEquals(1, result.warnings().size(), () -> "warnings: " + result.warnings());
        assertTrue(result.warnings().get(0).contains("not square"), result.warnings().get(0));
    }

    @Test
    void aTinyInputReportsAnUpscaleWarning() throws Exception {
        IconBuilder.Result result = IconBuilder.build(writeSourcePng(64), temp.resolve("zakazpro.ico"),
                temp.resolve("icons"));

        assertTrue(result.upscaled());
        assertEquals(1, result.warnings().size(), () -> "warnings: " + result.warnings());
        assertTrue(result.warnings().get(0).contains("256"), result.warnings().get(0));
    }

    @Test
    void scalingAlwaysProducesTheRequestedSize() {
        BufferedImage source = squarePng(300);
        for (int size : IconBuilder.ICO_SIZES) {
            BufferedImage scaled = IconBuilder.scale(source, size);
            assertEquals(size, scaled.getWidth());
            assertEquals(size, scaled.getHeight());
            assertEquals(BufferedImage.TYPE_INT_ARGB, scaled.getType());
        }
    }

    @Test
    void buildRejectsAMissingSource() {
        Path missing = temp.resolve("nope.png");
        IOException error = assertThrows(IOException.class,
                () -> IconBuilder.build(missing, temp.resolve("a.ico"), temp.resolve("icons")));
        assertTrue(error.getMessage().contains("not found"), error.getMessage());
    }

    @Test
    void theSizeListsAreTheDocumentedOnes() {
        assertArrayEquals(new int[] { 16, 24, 32, 48, 64, 128, 256 }, IconBuilder.ICO_SIZES);
        assertArrayEquals(new int[] { 256, 64, 32 }, IconBuilder.PNG_SIZES);
        assertEquals(256, IconBuilder.MIN_SOURCE_SIZE);
    }

    private Path writeSourcePng(int size) throws IOException {
        Path file = temp.resolve("source-" + size + "-" + System.nanoTime() + ".png");
        ImageIO.write(squarePng(size), "png", file.toFile());
        return file;
    }

    private static BufferedImage squarePng(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0x1E3A8A));
            g.fillRect(0, 0, size, size);
            g.setColor(new Color(0xF3F6FB));
            g.fillOval(size / 4, size / 4, size / 2, size / 2);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage widePng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0x15803D));
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }
}
