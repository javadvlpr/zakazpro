package com.mebelsex.ui.components;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard for the crash that JavaFX raises when a {@code TextInputControl} is modified from its own
 * text listener ("The start must be <= the end"). A listener may observe the text, but any
 * rewriting must happen in a {@code TextFormatter} filter. This is a source scan (no toolkit).
 */
class NumericFieldListenerScanTest {

    private static final String[] MUTATORS = {
            "setText(", "positionCaret(", "replaceText(", "insertText(", "deleteText(", "selectRange(", "clear()",
    };

    @Test
    void noTextListenerMutatesItsOwnControl() throws Exception {
        List<String> offenders = new ArrayList<>();
        Path src = Paths.get("src/main/java/com/mebelsex/ui");
        try (Stream<Path> paths = Files.walk(src)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                String content = Files.readString(p);
                scan(content, p.toString(), "textProperty().addListener", offenders);
                scan(content, p.toString(), ".setOnKeyTyped", offenders);
            }
        }
        assertTrue(offenders.isEmpty(),
                "Rewrite the text inside a TextFormatter filter, never inside the control's own listener: "
                        + offenders);
    }

    private static void scan(String content, String file, String marker, List<String> offenders) {
        int idx = 0;
        while ((idx = content.indexOf(marker, idx)) >= 0) {
            int recvEnd = idx;
            int recvStart = recvEnd;
            while (recvStart > 0 && isIdentifierChar(content.charAt(recvStart - 1))) {
                recvStart--;
            }
            String receiver = content.substring(recvStart, recvEnd);
            String simple = receiver.contains(".") ? receiver.substring(receiver.lastIndexOf('.') + 1) : receiver;

            int open = idx + marker.length();
            int depth = 0;
            int i = open;
            for (; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        i++;
                        break;
                    }
                }
            }
            String body = content.substring(open, Math.min(i, content.length()));

            for (String mutator : MUTATORS) {
                boolean hit = false;
                if (!receiver.isEmpty()) {
                    hit = body.contains(receiver + "." + mutator)
                            || body.contains(simple + "." + mutator)
                            || body.contains("this." + mutator);
                }
                if (!hit) {
                    hit = Pattern.compile("(?<![\\w.])" + Pattern.quote(mutator)).matcher(body).find();
                }
                if (hit) {
                    offenders.add(file + " :: " + (receiver.isEmpty() ? "this" : receiver) + " -> " + mutator);
                }
            }
            idx = i;
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
