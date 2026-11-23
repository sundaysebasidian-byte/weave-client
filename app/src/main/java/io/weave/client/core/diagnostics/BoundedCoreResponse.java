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
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) return output;
            if (output.size() + count > limit) throw new IllegalStateException("Local response too large");
            output.write(buffer, 0, count);
        }
    }
}
