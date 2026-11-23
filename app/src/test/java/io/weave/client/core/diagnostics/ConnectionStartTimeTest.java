package io.weave.client.core.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionStartTimeTest {
    @Test public void sortsNewerFractionalSecondsFirst() {
        String exact = "2026-11-21T12:00:00Z";
        String fraction = "2026-11-21T12:00:00.000000001Z";
        assertTrue(ConnectionStartTime.parse(fraction).isAfter(ConnectionStartTime.parse(exact)));
        assertTrue(ConnectionStartTime.parse("2026-11-21T12:00:00.01Z")
            .isAfter(ConnectionStartTime.parse("2026-11-21T12:00:00.009Z")));
    }

    @Test public void respectsOffsetsAndHandlesUnknownStarts() {
        assertEquals(ConnectionStartTime.parse("2026-11-21T04:00:00-08:00"),
            ConnectionStartTime.parse("2026-11-21T12:00:00Z"));
        assertNull(ConnectionStartTime.parse(""));
        assertNull(ConnectionStartTime.parse("unknown"));
    }
}
