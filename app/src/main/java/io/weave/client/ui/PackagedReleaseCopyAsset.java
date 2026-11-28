package io.weave.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** One registry and I/O boundary for app-owned, APK-local release copy only. */
final class PackagedReleaseCopyAsset {
    enum Seal {
        AR("ar-EG", "العربية (مصر)", 197311, "0739e1699b5ccdc309593d95104b15015c518a4280ee5c63e5a20b21d005ba2b"),
        ES("es-MX", "Español (México)", 172223, "0d3c74ab44faf35ab00d3ca47352f763dc4f178e2dae8dc7ebe31e82a47dc22f"),
        KO("ko-KR", "한국어 (대한민국)", 179957, "ce3ee36e62cf3a048f915b5d7d11f2cce265392be092d6cce5de1214754dfdbe"),
        RU("ru-RU", "Русский", 216186, "fc8e93a063052b0e74495dcd2d081ca08e11bc8119f2098c814b226ac4f6b28c"),
        HI("hi-IN", "हिन्दी", 243889, "425b93476618522d9ef01e1c743e706f637755840630388077cc3162c32f97dc");

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
