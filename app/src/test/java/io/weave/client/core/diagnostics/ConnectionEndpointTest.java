package io.weave.client.core.diagnostics;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ConnectionEndpointTest {
    @Test public void ipv6WithPortIsUnambiguous() {
        assertEquals("[2001:db8::1]:443", ConnectionEndpoint.withPort("2001:db8::1", "443"));
        assertEquals("[::1]:80", ConnectionEndpoint.withPort("::1", "80"));
        assertEquals("[fe80::1%wlan0]:53", ConnectionEndpoint.withPort("fe80::1%wlan0", "53"));
    }

    @Test public void alreadyBracketedAndNonIpv6HostsKeepTheirShape() {
        assertEquals("[::1]:443", ConnectionEndpoint.withPort("[::1]", "443"));
        assertEquals("example.org:443", ConnectionEndpoint.withPort("example.org", "443"));
        assertEquals("192.0.2.1:80", ConnectionEndpoint.withPort("192.0.2.1", "80"));
    }
}
