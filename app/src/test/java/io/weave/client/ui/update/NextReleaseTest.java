package io.weave.client.ui.update;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class NextReleaseTest {
    static final String TAG = "weave-next-v0.4.0-rc5", LOCAL = "0.4.0-rc4";
    static final String APK = "Weave-Next-0.4.0-rc5-build111-arm64-v8a.apk";
    static final String URL = "https://github.com/" + NextRelease.REPO + "/releases/download/" + TAG + "/" + APK;
    static final String DIGEST = "sha256:eff1820ab8af53d58a3fb58a86f600dc80cf8f47dad635efee527b8757d91d53";

    static NextRelease parse(String tag, String apk, String url, String digest, long bytes) {
        return NextRelease.parse(tag, false, true, LOCAL, apk, digest, bytes, url);
    }

    @Test public void actualPublicRc5NamingAndMetadata() {
        NextRelease r = parse(TAG, APK, URL, DIGEST, 20_064_628);
        assertNotNull(r); assertEquals(111,r.build); assertEquals("0.4.0-rc5",r.version);
        assertTrue(r.upgrades(LOCAL,110)); assertFalse(r.upgrades(r.version,111));
        assertFalse(r.upgrades(r.version,112)); assertFalse(r.upgrades("0.4.0-rc6",100));
    }
    @Test public void rejectsOtherProductsAndUntrustedUrls() {
        for (String bad : new String[]{"windows-v0.4.0-rc5","v0.4.0-rc5",TAG + "/../foo",TAG + "-debug"})
            assertNull(parse(bad,APK,URL,DIGEST,1));
        for (String bad : new String[]{APK.replace("Next-",""),APK.replace("arm64-v8a","x86_64"),
            APK.replace("111","00111"),APK.replace("111","9999999999"),APK + ".sha256",APK.replace(".apk","-debug.apk")})
            assertNull(parse(TAG,bad,URL.replace(APK,bad),DIGEST,1));
        for (String bad : new String[]{URL + "?mirror=x",URL.replace("github.com", "example.org"),
            URL.replace("https:","http:"),URL.replace("sundaysebasidian-byte/", "someone-else/")})
            assertNull(parse(TAG,APK,bad,DIGEST,1));
        assertNull(parse(TAG,APK,URL,"",1)); assertNull(parse(TAG,APK,URL,DIGEST,0));
        assertNull(parse(TAG,APK,URL,DIGEST,NextRelease.MAX_BYTES+1));
    }
    @Test public void enforcesPrereleaseAndSemverPolicy() {
        assertTrue(NextRelease.allowedTag(TAG,LOCAL)); assertFalse(NextRelease.allowedTag(TAG,"0.4.0"));
        assertFalse(NextRelease.allowedTag(TAG + "-device-test",LOCAL));
        assertTrue(NextRelease.compareTags("weave-next-v0.4.0-rc10","weave-next-v0.4.0-rc9")>0);
        assertTrue(NextRelease.compareTags("weave-next-v0.4.0","weave-next-v0.4.0-rc99")>0);
        assertNull(NextRelease.parse(TAG,true,true,LOCAL,APK,DIGEST,1,URL));
        assertNull(NextRelease.parse(TAG,false,false,LOCAL,APK,DIGEST,1,URL));
        assertNull(NextRelease.parse(TAG,false,true,"0.4.0",APK,DIGEST,1,URL));
    }
    @Test public void durableOncePerDayGateAndManualOverride() throws Exception {
        AtomicLong last = new AtomicLong(); AtomicInteger count = new AtomicInteger();
        Runnable f = () -> { if (NextRelease.reserve(false,1000,()->true,last::get,
            t->{last.set(t);return true;})) count.incrementAndGet(); };
        Thread a=new Thread(f), b=new Thread(f); a.start();b.start();a.join();b.join();
        assertEquals(1,count.get()); assertFalse(NextRelease.due(999,1000));
        assertFalse(NextRelease.due(1000+NextRelease.INTERVAL-1,1000));
        assertTrue(NextRelease.due(1000+NextRelease.INTERVAL,1000));
        assertFalse(NextRelease.reserve(false,1000+NextRelease.INTERVAL,()->false,last::get,t->true));
        assertFalse(NextRelease.reserve(false,1000+NextRelease.INTERVAL,()->true,last::get,t->false));
        assertTrue(NextRelease.reserve(true,999,()->false,last::get,t->{last.set(t);return true;}));
        assertEquals(1000,last.get());
    }
    @Test public void correctedDeviceClockDoesNotBlockChecksUntilOldFutureDate() {
        long corrected = 90 * NextRelease.INTERVAL;
        AtomicLong last = new AtomicLong(corrected + 100 * NextRelease.INTERVAL);
        LongPredicateUpdate write = new LongPredicateUpdate(last);
        assertFalse(NextRelease.reserve(false, corrected, () -> false, last::get, write::persist));
        assertEquals(corrected + 100 * NextRelease.INTERVAL, last.get());
        assertFalse(NextRelease.reserve(false, corrected, () -> true, last::get, write::persist));
        assertEquals(corrected, last.get()); // Rebase once without contacting the release feed.
        assertFalse(NextRelease.reserve(false, corrected + NextRelease.INTERVAL - 1, () -> true, last::get, write::persist));
        assertTrue(NextRelease.reserve(false, corrected + NextRelease.INTERVAL, () -> true, last::get, write::persist));
    }

    private static class LongPredicateUpdate {
        final AtomicLong store;
        LongPredicateUpdate(AtomicLong store) { this.store = store; }
        boolean persist(long value) { store.set(value); return true; }
    }

    @Test public void rejectIneligibleBuildsAndRecognizeRateLimits() {
        assertTrue(NextRelease.eligible("release","io.weave.client.next",true));
        assertFalse(NextRelease.eligible("debug","io.weave.client.next",true));
        assertFalse(NextRelease.eligible("localOptimized","io.weave.client.next",true));
        assertFalse(NextRelease.eligible("release","io.weave.client",true));
        assertFalse(NextRelease.eligible("release","io.weave.client.next",false));
        assertTrue(NextRelease.limited(429,null,null)); assertTrue(NextRelease.limited(403,"0",null));
        assertFalse(NextRelease.limited(403,"50",null));
    }
}
