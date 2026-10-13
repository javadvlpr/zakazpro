package com.mebelsex.common;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tests for the visible product name, folder/file names and version. */
class AppInfoTest {

    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    @Test
    void productNameAndSlugAreSet() {
        assertEquals("ZakazPro", AppInfo.PRODUCT_NAME);
        assertEquals("ZakazPro", AppInfo.SLUG);
        assertFalse(AppInfo.PRODUCT_NAME.isBlank());
    }

    @Test
    void slugIsAsciiAlphanumericAndAValidFolderName() {
        assertTrue(AppInfo.SLUG.matches("[A-Za-z0-9]+"), "slug must be ASCII letters/digits only: " + AppInfo.SLUG);
        assertTrue(AppInfo.DATA_DIR_NAME.matches("[A-Za-z0-9 ]+"), AppInfo.DATA_DIR_NAME);
        assertTrue(AppInfo.DOCUMENTS_DIR_NAME.matches("[A-Za-z0-9 ]+"), AppInfo.DOCUMENTS_DIR_NAME);
        for (String name : new String[] { AppInfo.SLUG, AppInfo.DATA_DIR_NAME, AppInfo.DOCUMENTS_DIR_NAME }) {
            for (char forbidden : new char[] { '<', '>', ':', '"', '/', '\\', '|', '?', '*' }) {
                assertFalse(name.indexOf(forbidden) >= 0, name + " must be a valid Windows folder name");
            }
            assertFalse(name.endsWith("."), name + " must not end with a dot");
            assertFalse(WINDOWS_RESERVED.contains(name.toUpperCase()), name + " is a reserved name");
        }
    }

    @Test
    void logAndBackupNamesUseTheSlug() {
        assertEquals(AppInfo.SLUG.toLowerCase(), AppInfo.LOG_FILE_BASENAME);
        assertEquals("zakazpro_", AppInfo.BACKUP_FILE_PREFIX);
        assertTrue(AppInfo.BACKUP_FILE_PREFIX.matches("[a-z0-9_]+"), AppInfo.BACKUP_FILE_PREFIX);
        assertTrue(AppInfo.BACKUP_FILE_PREFIX.endsWith("_"), "prefix must end with _ before the timestamp");
    }

    @Test
    void windowTitleUsesTheCompanyNameOrJustTheProductName() {
        assertEquals("ZakazPro", AppInfo.windowTitle(null));
        assertEquals("ZakazPro", AppInfo.windowTitle(""));
        assertEquals("ZakazPro", AppInfo.windowTitle("   "));
        assertEquals("Buxoro Eshiklari \u2014 ZakazPro", AppInfo.windowTitle("Buxoro Eshiklari"));
        assertEquals("Buxoro Eshiklari \u2014 ZakazPro", AppInfo.windowTitle("  Buxoro Eshiklari  "));
    }

    @Test
    void versionEqualsThePomVersion() throws Exception {
        String pomVersion;
        try (var in = Files.newInputStream(Paths.get("pom.xml"))) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            pomVersion = projectVersion(document);
        }
        assertNotNull(pomVersion);
        assertEquals(pomVersion, AppInfo.VERSION, "AppInfo.VERSION must equal the pom <version>");
        assertEquals(pomVersion, AppInfo.version(), "version() must return the pom version in tests");
    }

    /** The project's own <version> (a direct child of <project>), not the parent's. */
    private static String projectVersion(Document document) {
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && "version".equals(element.getNodeName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }
}
