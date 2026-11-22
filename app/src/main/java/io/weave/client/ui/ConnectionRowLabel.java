package io.weave.client.ui;

import java.util.List;
import java.util.ListIterator;

/** Display text only: never changes rule names, connection state or chain contents. */
final class ConnectionRowLabel {
    private ConnectionRowLabel() {}

    static String format(String rule, List<String> chain) {
        int size = chain.size();
        boolean showRule = !isBlank(rule);
        if (size == 0) return showRule ? rule : "";
        if (size == 1) {
            String hop = chain.get(0);
            if (isBlank(hop)) return showRule ? rule : "";
            return showRule ? rule + " · " + hop : hop;
        }

        // Two or more hops always produce a nonblank joined label: even empty hops have an arrow.
        StringBuilder result = new StringBuilder();
        if (showRule) result.append(rule).append(" · ");
        ListIterator<String> hops = chain.listIterator(size);
        result.append(hops.previous());
        while (hops.hasPrevious()) result.append(" → ").append(hops.previous());
        return result.toString();
    }

    // Match Kotlin/JVM Char.isWhitespace, including nonbreaking spaces.
    private static boolean isBlank(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch) && !Character.isSpaceChar(ch)) return false;
        }
        return true;
    }
}
