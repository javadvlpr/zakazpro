package com.mebelsex.ui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the CSS fix for the clipped ComboBox/DatePicker text: the inner controls must be
 * reset so they do not inherit the outer border, padding and min-height.
 */
class ApplicationCssTest {

    private static String css() throws Exception {
        Path path = Paths.get("src/main/resources/css/application.css");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    void comboBoxAndDatePickerInnerControlsAreReset() throws Exception {
        String css = css();
        assertTrue(css.contains(".date-picker .text-field"),
                "application.css must reset .date-picker .text-field (clipped text / nested box)");
        assertTrue(css.contains(".combo-box .list-cell"),
                "application.css must reset .combo-box .list-cell (clipped text)");
        assertTrue(css.contains(".combo-box-base .arrow-button"),
                "application.css must reset .combo-box-base .arrow-button");
    }

    @Test
    void tabPaneHasDesignSystemStyling() throws Exception {
        String css = css();
        assertTrue(css.contains(".tab-pane > .tab-header-area"),
                "application.css must style the TabPane header area");
    }

    @Test
    void tableCellsAndHeadersAreHorizontallyCentred() throws Exception {
        String css = css();
        Matcher cell = Pattern.compile("(?m)^\\.table-view\\s+\\.table-cell\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(css);
        assertTrue(cell.find(), "application.css must define a .table-view .table-cell rule");
        assertTrue(Pattern.compile("-fx-alignment\\s*:\\s*center\\s*;").matcher(cell.group(1)).find(),
                "table data cells must be horizontally centred to line up with their headers");
        Matcher header = Pattern.compile("(?m)^\\.table-view\\s+\\.column-header\\s+\\.label\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(css);
        assertTrue(header.find(), "application.css must define a column-header label rule");
        assertTrue(Pattern.compile("-fx-alignment\\s*:\\s*center\\s*;").matcher(header.group(1)).find(),
                "column headers must be horizontally centred");
        // No table-cell / money-col rule may re-introduce left/right alignment.
        for (String block : css.split("}")) {
            if (block.contains("table-cell") || block.contains("money-col")) {
                assertTrue(!Pattern.compile("-fx-alignment\\s*:\\s*center-(left|right)").matcher(block).find(),
                        "table-cell / money-col must not use left/right alignment: " + block.trim());
            }
        }
    }

    @Test
    void textInputsUseTokensForTextAndPrompt() throws Exception {
        Matcher m = Pattern.compile("(?m)^\\.text-input\\s*\\{(.*?)\\}", Pattern.DOTALL).matcher(css());
        assertTrue(m.find(), "application.css must define a .text-input rule for text/prompt colour");
        String block = m.group(1);
        assertTrue(Pattern.compile("-fx-text-fill\\s*:\\s*-mx-[a-z0-9-]+").matcher(block).find(),
                ".text-input must set -fx-text-fill to a -mx-* token");
        assertTrue(Pattern.compile("-fx-prompt-text-fill\\s*:\\s*-mx-[a-z0-9-]+").matcher(block).find(),
                ".text-input must set -fx-prompt-text-fill to a -mx-* token");
        assertTrue(Pattern.compile("-fx-highlight-text-fill\\s*:\\s*-mx-[a-z0-9-]+").matcher(block).find(),
                ".text-input must set -fx-highlight-text-fill to a -mx-* token");
        assertTrue(css().contains(".text-input:disabled"),
                "application.css must style disabled text inputs with the disabled tokens");
        assertTrue(css().contains(".table-view .placeholder .label"),
                "application.css must style the empty-table placeholder label");
    }
}
