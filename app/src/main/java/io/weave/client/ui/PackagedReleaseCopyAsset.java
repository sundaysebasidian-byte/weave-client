package io.weave.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** One registry and I/O boundary for app-owned, APK-local release copy only. */
final class PackagedReleaseCopyAsset {
    enum Seal {
        AR("ar-EG", "العربية (مصر)", 195955, "90bd8ad69c11060d6e716b2bf46c19cae6420b195a49901a52cc0bba1f30d76a"),
        ES("es-MX", "Español (México)", 171065, "9bfd284fe8f547e0851d2b5fd7dc0b18bd6198cc19ad85260c41b44126126615"),
        KO("ko-KR", "한국어 (대한민국)", 178793, "ebe901d1532dcc6ad1354de137e0e616f2018bf6f27624764bf73a79b51a72c0"),
        RU("ru-RU", "Русский", 217962, "fac6d3d4ca0f8697898ea55907536bfaefddb11071175420cd3f0161cdd35c53"),
        HI("hi-IN", "हिन्दी", 249119, "c619b309128278ab145d8e777f1a522e9e549c68c812d57c738ea14a71be3b0e");

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
