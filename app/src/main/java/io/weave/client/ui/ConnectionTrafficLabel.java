package io.weave.client.ui;

/** Isolates the display-only arrow, amount and unit as one left-to-right token. */
final class ConnectionTrafficLabel {
    private ConnectionTrafficLabel() {}

    static String format(String upload, String download) {
        // Four control/arrow units on each side, plus the three-character separator.
        var text = new StringBuilder(upload.length() + download.length() + 11);
        appendGroup(text, '↑', upload);
        text.append(" · ");
        appendGroup(text, '↓', download);
        return text.toString();
    }

    private static void appendGroup(StringBuilder text, char arrow, String amountWithUnit) {
        text.append('\u2066').append(arrow).append('\u00A0');
        for (int i = 0; i < amountWithUnit.length(); i++) {
            char ch = amountWithUnit.charAt(i);
            text.append(ch == ' ' ? '\u00A0' : ch);
        }
        text.append('\u2069');
    }
}
