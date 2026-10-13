package com.mebelsex.release;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Windows icon assets from the owner's square PNG, using only the JDK
 * (javax.imageio + java.awt, no new library).
 *
 * <p>
 * Outputs
 * <ul>
 * <li>{@code zakazpro.ico} with 16, 24, 32, 48, 64, 128 and 256 px images. The 256 px image
 * is stored as PNG; the smaller ones are 32-bit BMP/DIB entries with the AND mask, which the
 * widest range of Windows versions reads correctly.</li>
 * <li>{@code app-256.png}, {@code app-64.png} and {@code app-32.png} for the window icons.</li>
 * </ul>
 *
 * <p>
 * A non-square source is padded (never stretched) to a transparent square. Large reductions
 * are done by repeated halving before the final bicubic step, which keeps the small sizes
 * sharp. A source below 256 px is upscaled and reported as a warning.
 *
 * <p>
 * Runnable: {@code java -cp target/classes com.mebelsex.release.IconBuilder <source.png> <output dir>}
 */
public final class IconBuilder {

    /** Sizes stored in the .ico, in the order written. */
    public static final int[] ICO_SIZES = { 16, 24, 32, 48, 64, 128, 256 };

    /** Sizes exported as standalone PNGs for the JavaFX window icons. */
    public static final int[] PNG_SIZES = { 256, 64, 32 };

    /** The largest size a source image must have to avoid upscaling. */
    public static final int MIN_SOURCE_SIZE = 256;

    private static final int ICONDIR_SIZE = 6;
    private static final int ICONDIRENTRY_SIZE = 16;
    private static final int BITMAPINFOHEADER_SIZE = 40;
    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    private IconBuilder() {
    }

    /** What was written and anything the owner should know about the source image. */
    public record Result(int sourceWidth, int sourceHeight, boolean upscaled, List<Path> written,
            List<String> warnings) {
    }

    /**
     * Builds the .ico and the window PNGs.
     *
     * @param source   the owner's PNG (may be non-square)
     * @param icoFile  where to write the .ico
     * @param iconsDir directory for {@code app-256.png}, {@code app-64.png}, {@code app-32.png}
     */
    public static Result build(Path source, Path icoFile, Path iconsDir) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Icon source not found: " + source);
        }
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) {
            throw new IOException("Not an image: " + source);
        }
        List<String> warnings = new ArrayList<>();
        int width = original.getWidth();
        int height = original.getHeight();
        boolean upscaled = Math.min(width, height) < MIN_SOURCE_SIZE;
        if (width != height) {
            warnings.add("Source " + width + "x" + height + " is not square; padded to "
                    + Math.max(width, height) + "x" + Math.max(width, height) + " with transparency.");
        }
        if (upscaled) {
            warnings.add("Source is smaller than " + MIN_SOURCE_SIZE + " px (" + width + "x" + height
                    + "); sizes above that are upscaled and may look soft.");
        }

        BufferedImage square = square(original);
        List<IcoImage> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            BufferedImage scaled = scale(square, size);
            images.add(size == 256 ? new IcoImage(size, toPng(scaled), true) : new IcoImage(size, toDib(scaled), false));
        }

        List<Path> written = new ArrayList<>();
        Path icoParent = icoFile.toAbsolutePath().getParent();
        if (icoParent != null) {
            Files.createDirectories(icoParent);
        }
        Files.write(icoFile, toIco(images));
        written.add(icoFile);

        Files.createDirectories(iconsDir);
        for (int size : PNG_SIZES) {
            Path png = iconsDir.resolve("app-" + size + ".png");
            ImageIO.write(scale(square, size), "png", png.toFile());
            written.add(png);
        }
        return new Result(width, height, upscaled, List.copyOf(written), List.copyOf(warnings));
    }

    /** Pads a non-square image to a transparent square without stretching it. */
    public static BufferedImage square(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        if (width == height) {
            return toArgb(src);
        }
        int side = Math.max(width, height);
        BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, (side - width) / 2, (side - height) / 2, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    /** High quality resize: repeated halving, then one bicubic step. */
    public static BufferedImage scale(BufferedImage src, int target) {
        BufferedImage current = toArgb(src);
        int width = current.getWidth();
        int height = current.getHeight();
        while (width > target && height > target && width / 2 >= target && height / 2 >= target) {
            width /= 2;
            height /= 2;
            current = drawScaled(current, width, height);
        }
        if (width != target || height != target) {
            current = drawScaled(current, target, target);
        }
        return current;
    }

    private static BufferedImage drawScaled(BufferedImage src, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("No PNG writer available");
        }
        return bytes.toByteArray();
    }

    /**
     * A 32-bit DIB: BITMAPINFOHEADER (biHeight doubled to cover the AND mask), the BGRA
     * pixels bottom-up and the 1-bit AND mask.
     */
    static byte[] toDib(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int xorStride = ((width * 32 + 31) / 32) * 4;
        int andStride = ((width + 31) / 32) * 4;
        int xorSize = xorStride * height;
        int andSize = andStride * height;
        byte[] out = new byte[BITMAPINFOHEADER_SIZE + xorSize + andSize];

        ByteBuffer header = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(BITMAPINFOHEADER_SIZE);
        header.putInt(width);
        header.putInt(height * 2);
        header.putShort((short) 1);
        header.putShort((short) 32);
        header.putInt(0);
        header.putInt(xorSize + andSize);
        header.putInt(0);
        header.putInt(0);
        header.putInt(0);
        header.putInt(0);

        for (int y = height - 1; y >= 0; y--) {
            int rowStart = BITMAPINFOHEADER_SIZE + (height - 1 - y) * xorStride;
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int index = rowStart + x * 4;
                out[index] = (byte) (argb & 0xff);
                out[index + 1] = (byte) ((argb >> 8) & 0xff);
                out[index + 2] = (byte) ((argb >> 16) & 0xff);
                out[index + 3] = (byte) ((argb >>> 24) & 0xff);
            }
        }

        int andBase = BITMAPINFOHEADER_SIZE + xorSize;
        for (int y = height - 1; y >= 0; y--) {
            int rowStart = andBase + (height - 1 - y) * andStride;
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha < 128) {
                    out[rowStart + (x >> 3)] |= (byte) (0x80 >> (x & 7));
                }
            }
        }
        return out;
    }

    /** One image inside the .ico. */
    private record IcoImage(int size, byte[] data, boolean png) {
    }

    private static byte[] toIco(List<IcoImage> images) {
        int count = images.size();
        int offset = ICONDIR_SIZE + count * ICONDIRENTRY_SIZE;
        ByteBuffer buffer = ByteBuffer.allocate(offset + images.stream().mapToInt(i -> i.data().length).sum())
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0);
        buffer.putShort((short) 1);
        buffer.putShort((short) count);
        for (IcoImage image : images) {
            buffer.put((byte) (image.size() >= 256 ? 0 : image.size()));
            buffer.put((byte) (image.size() >= 256 ? 0 : image.size()));
            buffer.put((byte) 0);
            buffer.put((byte) 0);
            buffer.putShort((short) 1);
            buffer.putShort((short) 32);
            buffer.putInt(image.data().length);
            buffer.putInt(offset);
            offset += image.data().length;
        }
        for (IcoImage image : images) {
            buffer.put(image.data());
        }
        return buffer.array();
    }

    /** True when the byte block starts with the PNG signature. */
    public static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            ConsoleOutput.line("Usage: IconBuilder <source.png> <output dir>");
            ConsoleOutput.line("Writes <output dir>/zakazpro.ico and app-256.png, app-64.png, app-32.png.");
            System.exit(2);
            return;
        }
        Path source = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]).toAbsolutePath();
        Result result = build(source, outDir.resolve("zakazpro.ico"), outDir);
        for (String warning : result.warnings()) {
            ConsoleOutput.line("WARNING: " + warning);
        }
        for (Path path : result.written()) {
            ConsoleOutput.line("WROTE " + path + " (" + Files.size(path) + " bytes)");
        }
        ConsoleOutput.line("SOURCE " + result.sourceWidth() + "x" + result.sourceHeight());
    }
}
