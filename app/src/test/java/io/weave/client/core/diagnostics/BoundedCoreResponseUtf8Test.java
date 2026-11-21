package io.weave.client.core.diagnostics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BoundedCoreResponseUtf8Test {
    @Test
    public void exactByteLimitAndUtf8SpanningReadChunksKeepTheSameText() throws IOException {
        String text = "\ufeff" + "a".repeat(8188) + "\ud83d\ude80/北京";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertEquals(text, BoundedCoreResponse.readUtf8(new ByteArrayInputStream(bytes), bytes.length));
        assertEquals("", BoundedCoreResponse.readUtf8(new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test
    public void byteClientKeepsRawBytesWhileTextClientKeepsReplacementDecoding() throws IOException {
        byte[] bytes = {'a', (byte) 0xc2, ' ', (byte) 0xe2, (byte) 0x82};
        assertEquals("a\ufffd \ufffd", BoundedCoreResponse.readUtf8(new ByteArrayInputStream(bytes), bytes.length));
        assertTrue(Arrays.equals(bytes,
                BoundedCoreResponse.readBytes(new ByteArrayInputStream(bytes), bytes.length)));
    }

    @Test
    public void oversizePrecedesLaterIoErrorButWithinLimitIoErrorIsUnchanged() {
        IOException failure = new IOException("fixture read interrupted");
        try {
            BoundedCoreResponse.readUtf8(malformedThenFails(failure), 0);
            fail("Oversize must be rejected at the first chunk");
        } catch (IllegalStateException expected) {
            assertEquals("Local response too large", expected.getMessage());
        } catch (IOException wrong) {
            throw new AssertionError("Oversize must take precedence", wrong);
        }

        try {
            BoundedCoreResponse.readUtf8(malformedThenFails(failure), 1);
            fail("An interrupted reply must not return decoded partial text");
        } catch (IOException actual) {
            assertSame(failure, actual);
        }
    }

    private static InputStream malformedThenFails(IOException failure) {
        return new InputStream() {
            private boolean emitted;

            @Override public int read() {
                throw new AssertionError("Only bounded chunk reads are used");
            }

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (emitted) throw failure;
                emitted = true;
                bytes[offset] = (byte) 0xc2;
                return 1;
            }

            @Override public void close() {
                throw new AssertionError("The caller owns the stream");
            }
        };
    }
}
