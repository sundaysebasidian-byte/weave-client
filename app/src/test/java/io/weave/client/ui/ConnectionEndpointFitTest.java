package io.weave.client.ui;

import java.text.Bidi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConnectionEndpointFitTest {
    @Test public void exactInlineBoundaryAndOnePixelOverflow() {
        assertEquals(ConnectionEndpointFit.INLINE, ConnectionEndpointFit.choose(234, 282, 48));
        assertEquals(ConnectionEndpointFit.FULL_WIDTH, ConnectionEndpointFit.choose(235, 282, 48));
    }

    @Test public void usesWholeRowBeforeAllowingHorizontalPan() {
        assertEquals(ConnectionEndpointFit.FULL_WIDTH, ConnectionEndpointFit.choose(282, 282, 48));
        assertEquals(ConnectionEndpointFit.SCROLL, ConnectionEndpointFit.choose(283, 282, 48));
        assertEquals(ConnectionEndpointFit.SCROLL, ConnectionEndpointFit.choose(680, 282, 48));
    }

    @Test public void wideRowsRetainTheirInlineLayoutAndNoSpaceForCloseStacks() {
        assertEquals(ConnectionEndpointFit.INLINE, ConnectionEndpointFit.choose(270, 512, 48));
        assertEquals(ConnectionEndpointFit.FULL_WIDTH, ConnectionEndpointFit.choose(26, 47, 48));
    }

    @Test public void explicitLtrKeepsSyntheticEndpointsInASingleForwardJvmRun() {
        for (String text : new String[] {"[2001:db8::4]:443", "192.0.2.7:8443"}) {
            Bidi bidi = new Bidi(text, Bidi.DIRECTION_LEFT_TO_RIGHT);
            assertEquals(text, 1, bidi.getRunCount());
            assertEquals(text, 0, bidi.getRunLevel(0));
            assertEquals(text, text.length(), bidi.getRunLimit(0));
        }
    }
}
