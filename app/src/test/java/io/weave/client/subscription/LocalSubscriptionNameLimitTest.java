package io.weave.client.subscription;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocalSubscriptionNameLimitTest {
    private static final String ROCKET = "\ud83d\ude80";

    @Test
    public void cutsBeforeAPairThatCrossesTheUtf16Limit() {
        String prefix = "文".repeat(79);
        String result = LocalSubscriptionNameLimit.truncate(prefix + ROCKET + " suffix");

        assertEquals(prefix, result);
        assertEquals(result, new String(result.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @Test
    public void keepsTheWholePairIfItFitsExactlyAtTheLimit() {
        String expected = "文".repeat(78) + ROCKET;
        assertEquals(expected, LocalSubscriptionNameLimit.truncate(expected + " suffix"));
    }

    @Test
    public void ordinaryNamesKeepTheirOriginalUtf16Cap() {
        assertEquals("文".repeat(80), LocalSubscriptionNameLimit.truncate("文".repeat(81)));
        assertEquals("短文件", LocalSubscriptionNameLimit.truncate("短文件"));
    }
}
