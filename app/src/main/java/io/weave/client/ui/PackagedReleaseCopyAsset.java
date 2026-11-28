package io.weave.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** One registry and I/O boundary for app-owned, APK-local release copy only. */
final class PackagedReleaseCopyAsset {
    enum Seal {
        AR("ar-EG", "العربية (مصر)", 197891, "8d55ef04bea5dde052a859108a0e1b8c88fb77f46d4997bbb298e43bb19b3633"),
        ES("es-MX", "Español (México)", 172716, "8829fdfc2ec905bfd6fe32406dbcee306dcae96d265021c88db4ef44e46ec6ac"),
        KO("ko-KR", "한국어 (대한민국)", 180480, "d76fdc0cbb5f255480f77361ec288a99b7c3740da7e923b549c98a4a81b637a0"),
        RU("ru-RU", "Русский", 216857, "ca502f19da6a89b7f282f7558efe7e6d78f721906a4813e5041ea420acde70a4"),
        HI("hi-IN", "हिन्दी", 244629, "537a904f3d30015887ac4e14a507bc76626b4b4d0a8315cab5e47f2955d4b573");

        final String tag;
        final String label;
        final int bytes;
        final String sha256;

        Seal(String tag, String label, int bytes, String sha256) {
            this.tag = tag;
            this.label = label;
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    static Seal sealFor(String tag) {
        if (tag == null) return null;
        for (Seal seal : Seal.values()) {
            if (seal.tag.equals(tag)) return seal;
        }
        return null;
    }

    @FunctionalInterface
    interface AssetOpen {
        InputStream open(String path) throws IOException;
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(String tag, InputStream input) throws IOException;
    }

    private PackagedReleaseCopyAsset() {}

    static String assetPath(String tag) {
        return sealFor(tag) != null ? "weave-locales/" + tag + ".jsonl" : null;
    }

    static <T> T optionalObservation(Supplier<T> observe) {
        try {
            return observe.get();
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException unavailable) {
            // Optional platform/catalog observation cannot block otherwise healthy startup.
            return null;
        }
    }

    enum Tap { APPLIED, NOT_APPLIED, UNCONFIRMED }

    /** 'desired' is an explicit UI choice; empty means following the system. Never retry a write. */
    static Tap commitExplicitTap(String desired, Supplier<String> read, Consumer<String> apply) {
        String before = optionalObservation(read);
        if (before == null) return Tap.NOT_APPLIED;
        if (desired.equals(before)) return Tap.APPLIED;
        return optionalObservation(() -> {
            apply.accept(desired);
            return desired.equals(read.get()) ? Tap.APPLIED : Tap.UNCONFIRMED;
        }) == Tap.APPLIED ? Tap.APPLIED : Tap.UNCONFIRMED;
    }

    static <T> T readIfPackaged(String tag, AssetOpen asset, Decoder<T> verified) {
        String path = assetPath(tag);
        if (path == null) return null;
        try (InputStream input = asset.open(path)) {
            return verified.decode(tag, input);
        } catch (IOException missingOrInvalid) {
            // An unshipped/corrupt asset never becomes the render context.
            return null;
        }
    }
}
