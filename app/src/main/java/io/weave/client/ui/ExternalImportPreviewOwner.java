package io.weave.client.ui;

import java.util.UUID;

/** Match the exact preview that initiated an import, even for identical input. */
final class ExternalImportPreviewOwner {
    private ExternalImportPreviewOwner() {}

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static boolean owns(String visible, String owner) {
        return visible != null && visible.equals(owner);
    }

    static boolean isRunning(String visible, String owner, boolean importInFlight) {
        return importInFlight && owns(visible, owner);
    }

    static boolean isBlockedByOtherImport(String visible, String owner, boolean importInFlight) {
        return importInFlight && !owns(visible, owner);
    }

    static boolean canConfirm(String visible, String tapped, boolean importInFlight, String completedOwner) {
        return !importInFlight && owns(visible, tapped) && !owns(tapped, completedOwner);
    }
}
