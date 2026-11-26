package io.weave.client.core.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Buffers non-streaming controller replies up to the caller's byte limit. */
final class BoundedCoreResponse {
    private BoundedCoreResponse() {}

    static String readUtf8(InputStream input, int limit) throws IOException {
        // Decode the owned buffer directly rather than copying the whole reply first.
        return read(input, limit).toString(StandardCharsets.UTF_8.name());
    }

    static byte[] readBytes(InputStream input, int limit) throws IOException {
        return read(input, limit).toByteArray();
    }

    private static ByteArrayOutputStream read(InputStream input, int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Negative response limit");
        int bufferSize = Math.min(8192, Math.max(1, limit));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[bufferSize];
        while (true) {
            int remaining = limit - output.size();
            int readSize = remaining < buffer.length ? remaining + 1 : buffer.length;
            int count = readSize == buffer.length ? input.read(buffer) : input.read(buffer, 0, readSize);
            if (count < 0) return output;
            if (count > remaining) throw new IllegalStateException("Local response too large");
            output.write(buffer, 0, count);
        }
    }
}
