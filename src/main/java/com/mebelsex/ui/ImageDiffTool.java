package com.mebelsex.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Dev-only helper (not used by the app): compares two snapshot folders
 * pixel-by-pixel and prints the percentage of differing pixels per image.
 * Run (dev only, refused unless the env var is set):
 * {@code MEBELSEX_SNAPSHOT_DIR=1 java -cp target/classes com.mebelsex.ui.ImageDiffTool <before> <after>}
 */
public final class ImageDiffTool {

    private ImageDiffTool() {
    }

    public static void main(String[] args) throws Exception {
        if (System.getenv("MEBELSEX_SNAPSHOT_DIR") == null) {
            System.err.println("ImageDiffTool is a dev tool; set MEBELSEX_SNAPSHOT_DIR to run it.");
            return;
        }
        if (args.length < 2) {
            System.err.println("Usage: ImageDiffTool <beforeDir> <afterDir>");
            return;
        }
        File before = new File(args[0]);
        File after = new File(args[1]);
        File[] files = before.listFiles((d, name) -> name.endsWith(".png"));
        if (files == null) {
            System.err.println("No PNGs in " + before);
            return;
        }
        Arrays.sort(files);
        double worst = 0;
        String worstName = "";
        int compared = 0;
        int differingOverHalf = 0;
        for (File f : files) {
            File other = new File(after, f.getName());
            if (!other.isFile()) {
                System.out.printf("%-45s MISSING in after%n", f.getName());
                continue;
            }
            BufferedImage a = ImageIO.read(f);
            BufferedImage b = ImageIO.read(other);
            if (a == null || b == null) {
                System.out.printf("%-45s UNREADABLE%n", f.getName());
                continue;
            }
            double percent = diffPercent(a, b);
            compared++;
            if (percent > 0.5) {
                differingOverHalf++;
            }
            if (percent >= worst) {
                worst = percent;
                worstName = f.getName();
            }
            System.out.printf("%-45s %6.3f%%%n", f.getName(), percent);
        }
        System.out.println("---");
        System.out.printf("Compared %d images. Worst: %s = %.3f%%. Files > 0.5%%: %d%n", compared, worstName, worst,
                differingOverHalf);
    }

    /** Percentage of pixels that differ (0..100). Different sizes -> 100. */
    public static double diffPercent(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 100.0;
        }
        long differing = 0;
        long total = (long) a.getWidth() * a.getHeight();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        return total == 0 ? 0 : (100.0 * differing / total);
    }
}
