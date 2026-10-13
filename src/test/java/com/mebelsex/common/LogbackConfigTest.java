package com.mebelsex.common;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackConfigTest {

    private static Path config() {
        return Paths.get("src/main/resources/logback-spring.xml");
    }

    @Test
    void configIsWellFormedXml() throws Exception {
        try (var in = Files.newInputStream(config())) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            assertEquals("configuration", document.getDocumentElement().getNodeName());
        }
    }

    @Test
    void configReferencesTheLogDirPropertyAndRetention() throws Exception {
        String text = new String(Files.readAllBytes(config()), StandardCharsets.UTF_8);
        assertTrue(text.contains("mebelsex.log.dir"),
                "the log directory must come from the mebelsex.log.dir system property");
        assertTrue(text.contains("zakazpro.log"), "the active log file must be zakazpro.log");
        assertTrue(text.contains("<maxHistory>14</maxHistory>"), "logs kept for 14 days");
        assertTrue(text.contains("<totalSizeCap>50MB</totalSizeCap>"), "total size cap 50 MB");
        assertTrue(text.contains("\"CONSOLE\""), "a console appender must stay for dev");
        assertTrue(text.contains("\"FILE\""), "a file appender must exist");
    }
}
