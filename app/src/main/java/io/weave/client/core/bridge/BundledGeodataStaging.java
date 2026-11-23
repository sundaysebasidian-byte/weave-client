package io.weave.client.core.bridge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Owns one pending file for the duration of a serialized bundled-data install. */
final class BundledGeodataStaging {
    private BundledGeodataStaging() {}

    interface Write {
        void run() throws IOException;
    }

    static void run(File pending, Write write) throws IOException {
        Path path = pending.toPath();
        // Keep the installer's original, non-recursive pre-delete and copy behavior.
        boolean ownsStage = pending.delete() || Files.notExists(path, LinkOption.NOFOLLOW_LINKS);
        try {
            write.run();
        } finally {
            // Promotion consumes the stage. After failure, remove only the staged regular
            // file; a failed best-effort cleanup must not hide the original write error.
            if (ownsStage && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                pending.delete();
            }
        }
    }
}
