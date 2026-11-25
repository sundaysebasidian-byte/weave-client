package io.weave.client.ui;

/** Compact route badges allow one Unicode code point, which can occupy two UTF-16 units. */
final class RouteBadgeMonogram {
    private RouteBadgeMonogram() {}

    static String first(String monogram) {
        if (monogram.isEmpty()) return monogram;
        return monogram.substring(0, Character.charCount(monogram.codePointAt(0)));
    }
}
