package io.weave.client.core.diagnostics;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

final class ConnectionStartTime {
    private ConnectionStartTime() {}

    static Instant parse(String timestamp) {
        int length = timestamp.length();
        if (length == 0) return null;
        // Go RFC3339Nano UTC timestamps have a narrow, ASCII shape. Keep all
        // other forms on the existing ISO offset parser.
        if (length >= 20 && length <= 30 && timestamp.charAt(length - 1) == 'Z'
                && timestamp.charAt(4) == '-' && timestamp.charAt(7) == '-'
                && timestamp.charAt(10) == 'T' && timestamp.charAt(13) == ':'
                && timestamp.charAt(16) == ':' && (length == 20 || (length > 21 && timestamp.charAt(19) == '.'))) {
            int year = digits(timestamp, 0, 4);
            int month = digits(timestamp, 5, 2);
            int day = digits(timestamp, 8, 2);
            int hour = digits(timestamp, 11, 2);
            int minute = digits(timestamp, 14, 2);
            int second = digits(timestamp, 17, 2);
            int fraction = length == 20 ? 0 : digits(timestamp, 20, length - 21);
            if ((year | month | day | hour | minute | second | fraction) < 0
                    || hour > 23 || minute > 59 || second > 59) return null;
            for (int places = length == 20 ? 9 : length - 21; places < 9; places++) fraction *= 10;
            try {
                long epoch = LocalDate.of(year, month, day).toEpochDay() * 86_400L;
                return Instant.ofEpochSecond(epoch + hour * 3600L + minute * 60L + second, fraction);
            } catch (DateTimeException ignored) {
                return null;
            }
        }
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static int digits(String text, int start, int count) {
        int value = 0;
        for (int i = start; i < start + count; i++) {
            int digit = text.charAt(i) - '0';
            if (digit < 0 || digit > 9) return -1;
            value = value * 10 + digit;
        }
        return value;
    }

}
