package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window icons must ship with the application and decode (no JavaFX toolkit needed). */
class AppIconsTest {

    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>(Map.of(
            "/icons/app-256.png", 256,
            "/icons/app-64.png", 64,
            "/icons/app-32.png", 32));

    @Test
    void everyIconResourceExistsAndDecodesToTheExpectedSize() throws Exception {
        for (Map.Entry<String, Integer> entry : EXPECTED.entrySet()) {
            try (InputStream in = AppIcons.class.getResourceAsStream(entry.getKey())) {
                assertNotNull(in, entry.getKey() + " must be on the classpath");
                BufferedImage image = ImageIO.read(in);
                assertNotNull(image, entry.getKey() + " must decode");
                assertEquals(entry.getValue().intValue(), image.getWidth(), entry.getKey() + " width");
                assertEquals(entry.getValue().intValue(), image.getHeight(), entry.getKey() + " height");
                assertTrue(image.getColorModel().hasAlpha(), entry.getKey() + " must keep its transparency");
            }
        }
    }

    /** The .ico committed for the installer must stay a real 7-image Windows icon. */
    @Test
    void theCommittedInstallerIconIsValid() throws Exception {
        Path ico = Paths.get("installer", "assets", "zakazpro.ico");
        assertTrue(Files.isRegularFile(ico), "installer/assets/zakazpro.ico must be committed");
        byte[] bytes = Files.readAllBytes(ico);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, header.getShort(0), "reserved");
        assertEquals(1, header.getShort(2), "type");
        assertEquals(7, header.getShort(4), "image count");
    }
}
