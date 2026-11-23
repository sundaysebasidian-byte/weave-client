package io.weave.client.core.bridge;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

/** Checks the packaged payload without extracting or loading native code. */
final class NativeApkPayloadProbe {
    private NativeApkPayloadProbe() {}

    interface ArchiveOpener {
        ZipFile open(String path) throws IOException;
    }

    static boolean containsPair(List<String> paths, String[] abis) {
        return containsPair(paths, abis, ZipFile::new);
    }

    static boolean containsPair(List<String> paths, String[] abis, ArchiveOpener opener) {
        if (abis.length == 0) return false;
        for (String path : paths) {
            try (ZipFile archive = opener.open(path)) {
                for (String abi : abis) {
                    String prefix = "lib/" + abi + "/";
                    try {
                        if (archive.getEntry(prefix + "libbridge.so") != null
                                && archive.getEntry(prefix + "libclash.so") != null) {
                            return true;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // A bad entry must not hide an otherwise readable ABI in this archive.
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // An unreadable archive must not hide a valid pair in another split.
            }
        }
        return false;
    }
}
