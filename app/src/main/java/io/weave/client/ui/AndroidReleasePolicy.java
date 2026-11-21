package io.weave.client.ui;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AndroidReleasePolicy {
    static final String repo = "sundaysebasidian-byte/weave-client";
    static final long interval = 86_400_000L;
    private static final Pattern TAG = Pattern.compile("v([0-9]+\\.[0-9]+\\.[0-9]+)(-rc[1-9][0-9]*)?");
    static boolean due(long now, long last) { return last <= 0 || (now >= last && now - last >= interval); }
    static boolean eligible(String type, String id, boolean arm64) {
        return "release".equals(type) && "io.weave.client".equals(id) && arm64;
    }
    static synchronized boolean reserve(boolean manual, long now,
            java.util.function.BooleanSupplier enabled, java.util.function.LongSupplier read,
            java.util.function.LongPredicate persist) {
        if (!manual && (!enabled.getAsBoolean() || !due(now, read.getAsLong()))) return false;
        return persist.test(now);
    }
    static boolean limited(int status, String remaining, String retryAfter) {
        return status == 429 || (status == 403 && ("0".equals(remaining) || retryAfter != null));
    }
    static boolean preview(String version) {
        Matcher m = TAG.matcher("v" + version.replaceFirst("-device-test$", ""));
        return m.matches() && m.group(2) != null;
    }

    public static final class Release {
        final String tag;
        final int build;
        Release(String tag, int build) { this.tag = tag; this.build = build; }
        public String getPage() { return "https://github.com/" + repo + "/releases/tag/" + tag; }
    }

    static Release cached(String tag, int code, String localName, int localCode) {
        Matcher m = TAG.matcher(tag);
        if (!m.matches() || code <= localCode || (m.group(2) != null && !preview(localName))) return null;
        return new Release(tag, code);
    }

    static Release candidate(String tag, boolean draft, boolean prerelease, boolean preview, List<String> assets) {
        Matcher m = TAG.matcher(tag);
        if (!m.matches() || draft || (m.group(2) != null) != prerelease || (prerelease && !preview)) return null;
        Pattern apk = Pattern.compile("Weave-" + Pattern.quote(tag.substring(1))
                + "-build([1-9][0-9]{0,9})-Production-signed-arm64-v8a\\.apk");
        Release found = null;
        for (String name : assets) {
            Matcher asset = apk.matcher(name);
            if (!asset.matches()) continue;
            long code = Long.parseLong(asset.group(1));
            if (found != null || code > Integer.MAX_VALUE) return null;
            found = new Release(tag, (int) code);
        }
        return found;
    }
}
