package io.weave.client.subscription;

/** Caps a filename-derived name without cutting a valid supplementary character in half. */
final class LocalSubscriptionNameLimit {
    private static final int MAX_UTF16_UNITS = 80;

    private LocalSubscriptionNameLimit() {}

    static String truncate(String stem) {
        if (stem.length() <= MAX_UTF16_UNITS) return stem;
        int end = MAX_UTF16_UNITS;
        if (Character.isHighSurrogate(stem.charAt(end - 1)) && Character.isLowSurrogate(stem.charAt(end))) {
            end--;
        }
        return stem.substring(0, end);
    }
}
