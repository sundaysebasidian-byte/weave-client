package io.weave.client.core.vpn;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SocketUidExpiryWindowTest {
    @Test
    public void freshObservationsDoNotSweepTheHotCacheAtEachWrite() {
        var window = new SocketUidExpiryWindow();
        int sweeps = 0;
        for (long now = 1_000L; now < 1_000L + 2_048; now++) {
            if (window.needsSweep(now, 10_000L)) sweeps++;
            window.observe(now);
        }
        assertEquals(1, sweeps);
    }

    @Test
    public void earliestObservationIsValidAtTheExactTtl() {
        var window = new SocketUidExpiryWindow();
        window.observe(1_000L);
        window.observe(1_500L);

        assertFalse(window.needsSweep(11_000L, 10_000L));
        assertTrue(window.needsSweep(11_001L, 10_000L));
        window.clear();
        assertTrue(window.needsSweep(11_001L, 10_000L));
    }

    @Test
    public void regressingClocksAndOverflowUseTheOriginalSweep() {
        var window = new SocketUidExpiryWindow();
        window.observe(100L);
        window.observe(300L);
        assertTrue(window.needsSweep(200L, 10_000L));

        window.clear();
        window.observe(Long.MIN_VALUE);
        assertTrue(window.needsSweep(Long.MAX_VALUE, 10_000L));
    }
}
