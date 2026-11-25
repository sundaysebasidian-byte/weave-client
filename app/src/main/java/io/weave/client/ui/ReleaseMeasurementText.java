package io.weave.client.ui;

import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;

/** Changes display glyphs only. Measured values, rounding, and unit spellings stay canonical. */
final class ReleaseMeasurementText {
    private ReleaseMeasurementText() {}

    static String reading(String canonical, DecimalFormatSymbols symbols, boolean rtl) {
        if (symbols == null || "—".equals(canonical)) return canonical;
        StringBuilder result = new StringBuilder(canonical.length() + (rtl ? 2 : 0));
        if (rtl) result.append('\u2066');
        for (int i = 0; i < canonical.length(); i++) {
            char ch = canonical.charAt(i);
            if (ch >= '0' && ch <= '9') {
                result.append((char) (symbols.getZeroDigit() + ch - '0'));
            } else if (ch == '.') {
                result.append(symbols.getDecimalSeparator());
            } else if (rtl && ch == ' ' && i > 0 && i + 1 < canonical.length()
                    && canonical.charAt(i - 1) != '·' && canonical.charAt(i + 1) != '·') {
                // Bind a value to its unit, not across the inter-reading separators.
                result.append('\u00a0');
            } else {
                result.append(ch);
            }
        }
        if (rtl) result.append('\u2069');
        return result.toString();
    }

    static String canonicalDownload(double megabitsPerSecond, long kibibytes, long elapsedMillis) {
        // DownloadMeasurement guarantees positive elapsed time, but its whole-ms view floors.
        if (elapsedMillis == 0) {
            return String.format(Locale.ROOT, "%.2f Mbps · %d KiB · <1 ms", megabitsPerSecond, kibibytes);
        }
        return String.format(Locale.ROOT, "%.2f Mbps · %d KiB · %d ms",
                megabitsPerSecond, kibibytes, elapsedMillis);
    }

    static String timestamp(long epochMillis, Locale locale) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale)
                .format(new Date(epochMillis));
    }
}
