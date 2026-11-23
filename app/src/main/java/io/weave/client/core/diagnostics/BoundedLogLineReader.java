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
                return count == 0 || count > line.length ? "" : new String(chunk, start, count);
            }
            if (!dropped) {
                if (count > line.length - size) {
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
