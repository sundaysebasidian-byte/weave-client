package io.weave.client.subscription;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Matches old node rows in order during one repair. Add the old rows before taking any match. */
final class LegacyNodeIdIndex {
    private final UnaryOperator<String> decodeLegacyName;
    private final Map<Map.Entry<String, String>, ArrayDeque<Row>> exact = new HashMap<>();
    private final ArrayList<Row> possibleAliases = new ArrayList<>();
    private Map<Map.Entry<String, String>, ArrayDeque<Row>> aliases;

    LegacyNodeIdIndex(UnaryOperator<String> decodeLegacyName) {
        this.decodeLegacyName = decodeLegacyName;
    }

    void add(String id, String name, String protocol) {
        Row row = new Row(id, name, protocol);
        append(exact, name, row);
        // YamlNodeName changes only escaped names.
        if (name.indexOf('\\') >= 0) possibleAliases.add(row);
    }

    String take(String name, String protocol) {
        var key = new SimpleImmutableEntry<>(name, protocol);
        Row row = takeUnused(exact.get(key));
        if (row != null) return row.id;

        if (aliases == null) {
            if (possibleAliases.isEmpty()) return null;
            aliases = new HashMap<>();
            for (Row candidate : possibleAliases) {
                if (candidate.used) continue;
                String decoded = decodeLegacyName.apply(candidate.name);
                if (!decoded.equals(candidate.name)) append(aliases, decoded, candidate);
            }
            possibleAliases.clear();
        }
        row = takeUnused(aliases.get(key));
        return row != null ? row.id : null;
    }

    private static void append(Map<Map.Entry<String, String>, ArrayDeque<Row>> index, String name, Row row) {
        index.computeIfAbsent(new SimpleImmutableEntry<>(name, row.protocol), ignored -> new ArrayDeque<>(1))
                .addLast(row);
    }

    private static Row takeUnused(ArrayDeque<Row> candidates) {
        if (candidates == null) return null;
        Row row;
        while ((row = candidates.pollFirst()) != null) {
            if (!row.used) {
                row.used = true;
                return row;
            }
        }
        return null;
    }

    private static final class Row {
        final String id;
        final String name;
        final String protocol;
        boolean used;

        Row(String id, String name, String protocol) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
        }
    }
}
