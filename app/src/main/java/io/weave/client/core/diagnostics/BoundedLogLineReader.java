package io.weave.client.core.diagnostics;

import java.io.IOException;
import java.io.Reader;

/** Reads log records with fixed scratch space; returned strings outlive the buffer. */
final class BoundedLogLineReader {
    private final Reader input;
    private final char[] chunk = new char[2048];
    private final char[] line;
    private int position;
    private int end;
    private boolean skipLf;

    BoundedLogLineReader(Reader input, int maxLength) {
        this.input = input;
        line = new char[maxLength];
    }

    /** Returns null at EOF, and an empty string for overlength or empty records. */
    String readLine() throws IOException {
        return readLine(false);
    }

    void readHttpHeaders() throws IOException {
        String status = readLine(true);
        if (status == null || httpStatus(status) != 200) throw new IOException("log stream refused");
        int remaining = 16 * 1024 - status.length() - 2;
        while (true) {
            String header = readLine(true);
            if (header == null) throw new IOException("truncated log response headers");
            remaining -= header.length() + 2;
            if (remaining < 0) throw new IOException("log response headers too large");
            if (header.isEmpty()) return;
        }
    }

    static int httpStatus(String line) throws IOException {
        if ((!line.startsWith("HTTP/1.0 ") && !line.startsWith("HTTP/1.1 ")) ||
                line.length() < 12 || (line.length() > 12 && line.charAt(12) != ' ')) {
            throw new IOException("bad response status");
        }
        int code = 0;
        for (int index = 9; index < 12; index++) {
            char digit = line.charAt(index);
            if (digit < '0' || digit > '9') throw new IOException("bad response status");
            code = code * 10 + digit - '0';
        }
        return code;
    }

    private String readLine(boolean rejectOverlong) throws IOException {
        int size = 0;
        boolean dropped = false;
        while (true) {
            if (position >= end) {
                end = input.read(chunk);
                position = 0;
                if (end < 0) {
                    return dropped ? "" : size == 0 ? null : new String(line, 0, size);
                }
                if (end == 0) continue;
            }

            if (skipLf) {
                skipLf = false;
                if (chunk[position] == '\n') {
                    position++;
                    continue;
                }
            }
            int start = position;
            while (position < end && chunk[position] != '\n' && chunk[position] != '\r') position++;
            int count = position - start;
            if (position < end && size == 0 && !dropped) {
                // A complete row in the current chunk does not need the spanning-row buffer.
                skipLf = chunk[position++] == '\r';
                if (count > line.length && rejectOverlong) throw new IOException("log response header too long");
                return count == 0 || count > line.length ? "" : new String(chunk, start, count);
            }
            if (!dropped) {
                if (count > line.length - size) {
                    if (rejectOverlong) throw new IOException("log response header too long");
                    dropped = true;
                } else {
                    System.arraycopy(chunk, start, line, size, count);
                    size += count;
                }
            }
            if (position < end) {
                skipLf = chunk[position++] == '\r';
                return dropped ? "" : new String(line, 0, size);
            }
        }
    }
}
