package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the `.root` (light) and `.root.dark` (dark) token blocks of application.css and checks:
 * <ul>
 *   <li>the dark block overrides every {@code -mx-*} token defined in the light block
 *       (a small allow-list covers tokens that may legitimately stay the same);</li>
 *   <li>WCAG contrast is at least 4.5:1 for the main text/money pairs in BOTH themes
 *       (3.0:1 is allowed for the deliberately de-emphasised voided/disabled text).</li>
 * </ul>
 * No JavaFX toolkit is needed.
 */
class ThemeTokensTest {

    /** Tokens the dark block may leave equal to the light value. */
    private static final List<String> SHARED_ALLOWED = List.of("-mx-on-primary");

    private static final Pattern TOKEN = Pattern.compile("^\\s*(-mx-[a-z0-9-]+)\\s*:\\s*([^;]+);", Pattern.MULTILINE);

    private static String css() throws Exception {
        Path path = Paths.get("src/main/resources/css/application.css");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String block(String css, String selectorRegex) {
        Matcher m = Pattern.compile(selectorRegex + "\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(css);
        assertTrue(m.find(), "CSS block not found: " + selectorRegex);
        return m.group(1);
    }

    private static Map<String, String> tokens(String block) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = TOKEN.matcher(block);
        while (m.find()) {
            map.put(m.group(1), m.group(2).trim());
        }
        return map;
    }

    @Test
    void darkBlockOverridesEveryLightToken() throws Exception {
        Map<String, String> light = tokens(block(css(), "\\.root"));
        Map<String, String> dark = tokens(block(css(), "\\.root\\.dark"));
        for (String token : light.keySet()) {
            if (SHARED_ALLOWED.contains(token)) {
                continue;
            }
            assertTrue(dark.containsKey(token), ".root.dark must override " + token);
        }
    }

    @Test
    void contrastHoldsInBothThemes() throws Exception {
        Map<String, String> light = tokens(block(css(), "\\.root"));
        Map<String, String> dark = tokens(block(css(), "\\.root\\.dark"));
        checkTheme("light", light);
        checkTheme("dark", dark);
    }

    private void checkTheme(String name, Map<String, String> t) {
        assertRatio(name, "text/bg", t, "-mx-text", "-mx-bg", 4.5);
        assertRatio(name, "text/surface", t, "-mx-text", "-mx-surface", 4.5);
        assertRatio(name, "muted/surface", t, "-mx-muted", "-mx-surface", 4.5);
        assertRatio(name, "heading/bg", t, "-mx-heading", "-mx-bg", 4.5);
        assertRatio(name, "on-primary/primary", t, "-mx-on-primary", "-mx-primary", 4.5);
        assertRatio(name, "profit/surface", t, "-mx-profit", "-mx-surface", 4.5);
        assertRatio(name, "debt/surface", t, "-mx-debt", "-mx-surface", 4.5);
        assertRatio(name, "sidebar-text/sidebar-end", t, "-mx-sidebar-text", "-mx-sidebar-end", 4.5);
        assertRatio(name, "voided-text/surface", t, "-mx-voided-text", "-mx-surface", 3.0);
        assertRatio(name, "disabled-text/surface", t, "-mx-disabled-text", "-mx-surface", 3.0);
        assertRatio(name, "disabled-text/disabled-surface", t, "-mx-disabled-text", "-mx-disabled-surface", 3.0);
        for (String badge : List.of("new", "in-production", "ready", "delivered", "completed", "cancelled")) {
            assertRatio(name, "badge-" + badge, t, "-mx-badge-" + badge + "-fg", "-mx-badge-" + badge + "-bg", 4.5);
        }
    }

    private void assertRatio(String theme, String label, Map<String, String> t, String fgToken, String bgToken,
            double min) {
        String fg = t.get(fgToken);
        String bg = t.get(bgToken);
        assertTrue(fg != null, theme + ": missing " + fgToken);
        assertTrue(bg != null, theme + ": missing " + bgToken);
        double ratio = contrast(fg, bg);
        assertTrue(ratio >= min,
                String.format("%s: %s contrast %.2f < %.1f (%s on %s)", theme, label, ratio, min, fg, bg));
    }

    // ---- WCAG helpers -------------------------------------------------

    private static double contrast(String fgValue, String bgValue) {
        Rgba fg = parse(fgValue);
        Rgba bg = parse(bgValue);
        if (fg.a < 1.0) {
            fg = fg.over(bg); // composite translucent text over its background
        }
        double l1 = fg.luminance();
        double l2 = bg.luminance();
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static Rgba parse(String value) {
        String v = value.trim();
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            double a = hex.length() >= 8 ? Integer.parseInt(hex.substring(6, 8), 16) / 255.0 : 1.0;
            return new Rgba(r, g, b, a);
        }
        Matcher m = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+))?\\s*\\)")
                .matcher(v);
        assertTrue(m.matches(), "Unsupported colour: " + value);
        double a = m.group(4) == null ? 1.0 : Double.parseDouble(m.group(4));
        return new Rgba(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), a);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private record Rgba(int r, int g, int b, double a) {
        double luminance() {
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
        }

        Rgba over(Rgba bg) {
            int cr = (int) Math.round(r * a + bg.r * (1 - a));
            int cg = (int) Math.round(g * a + bg.g * (1 - a));
            int cb = (int) Math.round(b * a + bg.b * (1 - a));
            return new Rgba(cr, cg, cb, 1.0);
        }
    }
}
