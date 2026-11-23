package io.weave.client.apps;

import java.util.Locale;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LauncherLabelMonogramTest {
    @Test
    public void preservesCodePointBoundariesAndUppercasesAfterTakingThePrefix() {
        assertEquals("", LauncherLabelMonogram.of(""));
        assertEquals("A", LauncherLabelMonogram.of("a"));
        assertEquals("文🚀", LauncherLabelMonogram.of("文🚀社"));
        assertEquals("🚀A", LauncherLabelMonogram.of("🚀abc"));
        assertEquals("SSA", LauncherLabelMonogram.of("ßa!"));
        assertEquals("FFIB", LauncherLabelMonogram.of("ﬃb!"));
        assertEquals("II", LauncherLabelMonogram.of("iımore"));
        assertEquals("A\u0301", LauncherLabelMonogram.of("a\u0301b"));
        assertEquals("\uD800A", LauncherLabelMonogram.of("\uD800abc"));
        assertEquals("A\uDC00", LauncherLabelMonogram.of("a\uDC00B"));
    }

    @Test
    public void matchesThePreviousCodePointLoopForMixedUnicodeLabels() {
        String[] chunks = {"a", "ß", "ı", "ñ", "文", "\u0301", "🌙", "🚀",
                "\uD800", "\uDC00", "\u0000", " ", "ﬃ"};
        var fixture = new Random(69);
        for (int i = 0; i < 10_000; i++) {
            var label = new StringBuilder();
            for (int j = 0, n = fixture.nextInt(10); j < n; j++) {
                label.append(chunks[fixture.nextInt(chunks.length)]);
            }
            String input = label.toString();
            var previous = new StringBuilder();
            for (int codePoint : input.codePoints().limit(2).toArray()) {
                previous.append(new String(Character.toChars(codePoint)));
            }
            assertEquals(previous.toString().toUpperCase(Locale.ROOT), LauncherLabelMonogram.of(input));
        }
    }
}
