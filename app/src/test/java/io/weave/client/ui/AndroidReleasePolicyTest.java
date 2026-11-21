package io.weave.client.ui;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidReleasePolicyTest {
    private static final String rc = "v0.4.0-rc4";
    private static final String apk = "Weave-0.4.0-rc4-build110-Production-signed-arm64-v8a.apk";
    @Test public void channelsAndAssets() {
        AndroidReleasePolicy.Release r = AndroidReleasePolicy.candidate(rc, false, true, true, Collections.singletonList(apk));
        assertNotNull(r); assertEquals(110, r.build);
        assertEquals("https://github.com/sundaysebasidian-byte/weave-client/releases/tag/v0.4.0-rc4", r.getPage());
        assertNull(AndroidReleasePolicy.candidate(rc, false, true, false, Collections.singletonList(apk)));
        assertNull(AndroidReleasePolicy.candidate(rc, true, true, true, Collections.singletonList(apk)));
        assertNull(AndroidReleasePolicy.candidate(rc, false, false, true, Collections.singletonList(apk)));
        for (String bad : Arrays.asList("weave-win-x64.exe", "Weave-Experience-arm64-debug-build106.apk",
                apk.replace("arm64-v8a", "x86"), apk.replace("rc4", "rc3"),
                apk.replace("110", "9999999999"), apk + ".sha256")) {
            assertNull(AndroidReleasePolicy.candidate(rc, false, true, true, Collections.singletonList(bad)));
        }
        assertNull(AndroidReleasePolicy.candidate(rc, false, true, true, Arrays.asList(apk, apk)));
        assertNull(AndroidReleasePolicy.candidate("v0.4.0/../../evil", false, false, true, Collections.singletonList(apk)));
        assertNotNull(AndroidReleasePolicy.candidate("v0.4.1", false, false, false,
                Collections.singletonList("Weave-0.4.1-build111-Production-signed-arm64-v8a.apk")));
    }
    @Test public void installedBuildAndClock() {
        assertTrue(AndroidReleasePolicy.preview("0.4.0-rc4")); assertTrue(AndroidReleasePolicy.preview("0.4.0-rc4-device-test"));
        assertFalse(AndroidReleasePolicy.preview("0.4.0")); assertFalse(AndroidReleasePolicy.preview("unknown"));
        assertNull(AndroidReleasePolicy.cached("v0.4.0", 106, "0.4.0-rc4", 110));
        assertNull(AndroidReleasePolicy.cached(rc, 110, "0.4.0-rc4", 110));
        assertNull(AndroidReleasePolicy.cached(rc, 111, "0.4.0", 110));
        assertNotNull(AndroidReleasePolicy.cached("v0.4.1", 111, "0.4.0-rc4", 110));
        assertFalse(AndroidReleasePolicy.due(86400000, 1)); assertTrue(AndroidReleasePolicy.due(86400001, 1));
        assertFalse(AndroidReleasePolicy.due(100, 101)); assertTrue(AndroidReleasePolicy.due(100, 0));
    }
    @Test public void gateAndAccessStatus() throws Exception {
        java.util.concurrent.atomic.AtomicLong last = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger(0);
        Runnable startup = () -> {
            if (AndroidReleasePolicy.reserve(false, 1000, () -> true, last::get,
                    time -> { last.set(time); return true; })) requests.incrementAndGet();
        };
        Thread a = new Thread(startup), b = new Thread(startup);
        a.start(); b.start(); a.join(); b.join(); assertEquals(1, requests.get());
        assertFalse(AndroidReleasePolicy.reserve(false, 86401000L, () -> false, last::get, t -> true));
        assertFalse(AndroidReleasePolicy.reserve(false, 86401000L, () -> true, last::get, t -> false));
        assertTrue(AndroidReleasePolicy.reserve(true, 1001, () -> false, last::get, t -> true));
        assertTrue(AndroidReleasePolicy.eligible("release", "io.weave.client", true));
        assertFalse(AndroidReleasePolicy.eligible("localOptimized", "io.weave.client", true));
        assertFalse(AndroidReleasePolicy.eligible("debug", "io.weave.client", true));
        assertFalse(AndroidReleasePolicy.eligible("release", "io.weave.client.acceptance", true));
        assertFalse(AndroidReleasePolicy.eligible("release", "io.weave.client", false));
        assertFalse(AndroidReleasePolicy.limited(403, "55", null));
        assertTrue(AndroidReleasePolicy.limited(403, null, "60"));
        assertTrue(AndroidReleasePolicy.limited(403, "0", null));
        assertTrue(AndroidReleasePolicy.limited(429, null, null));
    }
}
