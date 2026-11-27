package io.weave.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** One registry and I/O boundary for app-owned, APK-local release copy only. */
final class PackagedReleaseCopyAsset {
    enum Seal {
        AR("ar-EG", "العربية (مصر)", 190434, "aed188dab5388adf39ebec20f213226782e670a04bc92cb3a70721f239957e38"),
        ES("es-MX", "Español (México)", 166337, "5c528d8f70e82642bd5e5030a86573d9b7971d653761f998e9bc966506a4933b"),
        KO("ko-KR", "한국어 (대한민국)", 173886, "604385b73058770138b36c30b7609f766a94b8d468f51efd13e7d14649fac02e");

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
