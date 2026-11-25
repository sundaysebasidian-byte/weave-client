package io.weave.client.ui;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RouteBadgeMonogramTest {
    @Test public void supplementaryLauncherMonogramSurvivesCollapsedBadge() {
        String monogram = "🚇M";
        assertArrayEquals("🚇".getBytes(StandardCharsets.UTF_8),
                RouteBadgeMonogram.first(monogram).getBytes(StandardCharsets.UTF_8));
        assertEquals("🚇M", monogram);
    }

    @Test public void keepsTheSingleCharacterDesignForBmpAndAsciiAndAllowsEmpty() {
        assertEquals("京", RouteBadgeMonogram.first("京大"));
        assertEquals("A", RouteBadgeMonogram.first("AB"));
        assertEquals("", RouteBadgeMonogram.first(""));
    }
}
