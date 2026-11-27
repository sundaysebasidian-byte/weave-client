package io.weave.client.data;

import java.util.Map;

/**
 * Decodes the dedicated release app-copy preference. It is not a migration from Android's
 * per-app setting, the six released choices, or isolated preview preferences.
 */
final class ReleaseAppCopySelection {
    static final String KEY = "release_app_copy_locale_v1";
    enum State { ABSENT, UNSUPPORTED, SELECTED }

    private static final ReleaseAppCopySelection ABSENT = new ReleaseAppCopySelection(State.ABSENT, null);
    private static final ReleaseAppCopySelection UNSUPPORTED = new ReleaseAppCopySelection(State.UNSUPPORTED, null);
    private static final ReleaseAppCopySelection ARABIC = new ReleaseAppCopySelection(State.SELECTED, "ar-EG");
    private static final ReleaseAppCopySelection SPANISH = new ReleaseAppCopySelection(State.SELECTED, "es-MX");
    private static final ReleaseAppCopySelection KOREAN = new ReleaseAppCopySelection(State.SELECTED, "ko-KR");

    final State state;
    final String canonicalTag;

    private ReleaseAppCopySelection(State state, String canonicalTag) {
        this.state = state;
        this.canonicalTag = canonicalTag;
    }

    static ReleaseAppCopySelection read(Map<String, ?> record) {
        if (!record.containsKey(KEY)) return ABSENT;
        // Do not normalize a stored spelling or cast an unexpected preference type.
        // Unknown input is distinguishable from absence, never eligible for implicit migration.
        Object stored = record.get(KEY);
        if (!(stored instanceof String tag)) return UNSUPPORTED;
        switch (tag) {
            case "ar-EG": return ARABIC;
            case "es-MX": return SPANISH;
            case "ko-KR": return KOREAN;
            default: return UNSUPPORTED;
        }
    }
}
