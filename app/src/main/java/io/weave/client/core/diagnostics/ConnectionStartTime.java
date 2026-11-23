package io.weave.client.core.diagnostics;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

final class ConnectionStartTime {
    private ConnectionStartTime() {}

    static Instant parse(String timestamp) {
        if (timestamp.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
