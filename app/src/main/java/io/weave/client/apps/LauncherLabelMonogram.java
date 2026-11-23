package io.weave.client.apps;

import java.util.Locale;

/** First two code points of a resolved launcher label, uppercased after taking that prefix. */
final class LauncherLabelMonogram {
    private LauncherLabelMonogram() {}

    static String of(String label) {
        int end = 0;
        for (int count = 0; count < 2 && end < label.length(); count++) {
            end += Character.charCount(label.codePointAt(end));
        }
        return label.substring(0, end).toUpperCase(Locale.ROOT);
    }
}
