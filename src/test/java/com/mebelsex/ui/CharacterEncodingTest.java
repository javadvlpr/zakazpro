package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterEncodingTest {

    @Test
    void testMessageEncodingAndEscaping() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages_uz");

        // 1. Basic UTF-8 verification
        String deleteStr = bundle.getString("ui.delete");
        assertEquals("O'chirish", deleteStr, "Basic apostrophe rendering failed");

        // 2. Parameterized message verification (checks the double apostrophe escape)
        String confirmRaw = bundle.getString("ui.customer.delete_confirm");
        assertTrue(confirmRaw.contains("o''chiril"), "Raw bundle should contain double apostrophes for escaping");

        String formatted = MessageFormat.format(confirmRaw, "Sardor");
        assertEquals("Mijoz Sardor o'chirilsinmi?", formatted,
                "MessageFormat should reduce double apostrophe to single");
    }
}
