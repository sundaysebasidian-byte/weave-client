package io.weave.client.ui.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

/** Normalized metadata, never an installer authorization. Adapted from R009's AndroidReleasePolicy. */
public final class NextRelease {
    public static final String REPO = "sundaysebasidian-byte/weave-client", PREFIX = "weave-next-v";
    public static final long MAX_BYTES = 96L * 1024 * 1024, INTERVAL = 86_400_000L;
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})(?:-rc([1-9][0-9]{0,5}))?");
    private static final Pattern SHA = Pattern.compile("sha256:[0-9a-f]{64}");
    public final String tag, version, name, digest, url;
    public final int build;
    public final long size;

    private NextRelease(String tag, String version, int build, String name, String digest, long size) {
        this.tag = tag; this.version = version; this.build = build; this.name = name;
        this.digest = digest; this.size = size;
        this.url = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + name;
    }

    static boolean eligible(String type, String id, boolean arm64) {
        return "release".equals(type) && "io.weave.client.next".equals(id) && arm64;
    }

    static boolean due(long now, long last) {
        return now > 0 && (last <= 0 || (now >= last && now - last >= INTERVAL));
    }

    static synchronized boolean reserve(boolean manual, long now, BooleanSupplier enabled,
                                         LongSupplier last, LongPredicate persist) {
        if (!manual && !enabled.getAsBoolean()) return false;
        long previous = last.getAsLong();
        if (!manual && now > 0 && previous > now) {
            // Re-anchor the daily gate without sending a request on a clock correction.
            persist.test(now);
            return false;
        }
        if (!manual && !due(now, previous)) return false;
        // A manual check never moves the reservation back if the wall clock rolls back.
        return persist.test(Math.max(now, previous));
    }

    static boolean limited(int status, String remaining, String retryAfter) {
        return status == 429 || (status == 403 && ("0".equals(remaining) || retryAfter != null));
    }

    static NextRelease parse(String tag, boolean draft, boolean prerelease, String localVersion,
                             String name, String githubDigest, long size, String browserUrl) {
        if (draft || !tag.startsWith(PREFIX)) return null;
        String version = tag.substring(PREFIX.length());
        Matcher target = VERSION.matcher(version), local = VERSION.matcher(localVersion);
        if (!target.matches() || !local.matches() || (target.group(4) != null) != prerelease ||
                (prerelease && local.group(4) == null)) return null;
        String prefix = "Weave-Next-" + version + "-build", suffix = "-arm64-v8a.apk";
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) return null;
        String code = name.substring(prefix.length(), name.length() - suffix.length());
        if (!code.matches("[1-9][0-9]{0,9}") || Long.parseLong(code) > Integer.MAX_VALUE ||
                size <= 0 || size > MAX_BYTES || !SHA.matcher(githubDigest).matches()) return null;
        NextRelease r = new NextRelease(tag, version, Integer.parseInt(code), name, githubDigest.substring(7), size);
        return r.url.equals(browserUrl) ? r : null;
    }

    boolean upgrades(String installedVersion, int installedBuild) {
        Matcher a = VERSION.matcher(version), b = VERSION.matcher(installedVersion);
        return build > installedBuild && a.matches() && b.matches() && compare(a, b) >= 0;
    }

    static boolean allowedTag(String tag, String localVersion) {
        if (!tag.startsWith(PREFIX)) return false;
        Matcher target = VERSION.matcher(tag.substring(PREFIX.length())), local = VERSION.matcher(localVersion);
        return target.matches() && local.matches() && (target.group(4) == null || local.group(4) != null);
    }

    static int compareTags(String first, String second) {
        Matcher a = VERSION.matcher(first.substring(PREFIX.length())), b = VERSION.matcher(second.substring(PREFIX.length()));
        if (!a.matches() || !b.matches()) throw new IllegalArgumentException("Invalid release tag");
        return compare(a,b);
    }

    private static int compare(Matcher a, Matcher b) {
        for (int i = 1; i <= 4; i++) {
            int x = a.group(i) == null ? Integer.MAX_VALUE : Integer.parseInt(a.group(i));
            int y = b.group(i) == null ? Integer.MAX_VALUE : Integer.parseInt(b.group(i));
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
}
