package io.weave.client.subscription;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LegacyNodeIdIndexTest {
    @Test
    public void exactRowsBeatEarlierAliasesAndAllDuplicatesStayInFifoOrder() {
        var index = new LegacyNodeIdIndex(value -> "Tokyo");
        index.add("legacy-00a", "\\u0054okyo", "socks5");
        index.add("exact-first", "Tokyo", "socks5");
        index.add("legacy-001", "\\x54okyo", "socks5");
        index.add("exact-last", "Tokyo", "socks5");
        index.add("another-protocol", "Tokyo", "http");

        assertEquals("exact-first", index.take("Tokyo", "socks5"));
        assertEquals("exact-last", index.take("Tokyo", "socks5"));
        assertEquals("legacy-00a", index.take("Tokyo", "socks5"));
        assertEquals("another-protocol", index.take("Tokyo", "http"));
        assertEquals("legacy-001", index.take("Tokyo", "socks5"));
        assertNull(index.take("Tokyo", "socks5"));
        assertNull(index.take("Tokyo", "Socks5"));
    }

    @Test
    public void aRowRemovedThroughEitherIndexCannotBeReusedThroughTheOther() {
        var index = new LegacyNodeIdIndex(value -> switch (value) {
            case "\\u0054okyo" -> "Tokyo";
            case "\\u004fsaka" -> "Osaka";
            default -> value;
        });
        index.add("old-first", "\\u0054okyo", "socks5");
        index.add("old-second", "\\u004fsaka", "socks5");

        assertEquals("old-first", index.take("Tokyo", "socks5")); // Builds the alias index.
        assertNull(index.take("\\u0054okyo", "socks5"));
        assertEquals("old-second", index.take("\\u004fsaka", "socks5"));
        assertNull(index.take("Osaka", "socks5"));
    }

    @Test
    public void exactMatchesNeverNeedTheLegacyDecoder() {
        var index = new LegacyNodeIdIndex(value -> { throw new AssertionError("unexpected decode"); });
        index.add("keep-009f", "\\u0046ixture", "socks5");
        index.add("keep-00Ab", "Fixture", "socks5");

        assertEquals("keep-009f", index.take("\\u0046ixture", "socks5"));
        assertEquals("keep-00Ab", index.take("Fixture", "socks5"));
    }

    @Test
    public void reverseOrderedLegacyMatchesDecodeEachPriorNameOnce() {
        int size = 1024;
        var decodes = new AtomicInteger();
        var index = new LegacyNodeIdIndex(value -> {
            decodes.incrementAndGet();
            return "N" + value.substring(6);
        });
        for (int i = 0; i < size; i++) index.add("stored-" + i, "\\u004e" + i, "socks5");
        for (int i = size - 1; i >= 0; i--) assertEquals("stored-" + i, index.take("N" + i, "socks5"));

        assertEquals(size, decodes.get());
    }
}
