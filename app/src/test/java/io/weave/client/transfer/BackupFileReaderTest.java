package io.weave.client.transfer;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public final class BackupFileReaderTest {
    @Test public void emptyAndExactLimit() throws IOException {
        assertArrayEquals(new byte[0], BackupFileReader.read(new ByteArrayInputStream(new byte[0]), 0));
        byte[] exact = new byte[128 * 1024 + 1];
        new Random(73).nextBytes(exact);
        assertArrayEquals(exact, BackupFileReader.read(new ByteArrayInputStream(exact), exact.length));
    }

    @Test public void shortReadsRetainEveryByte() throws IOException {
        byte[] data = new byte[128 * 1024 + 23];
        new Random(74).nextBytes(data);
        ByteArrayInputStream stream = new ByteArrayInputStream(data) {
            @Override public int available() { return 0; }
            @Override public int read(byte[] buffer, int offset, int count) {
                return super.read(buffer, offset, Math.min(23, count));
            }
        };
        assertArrayEquals(data, BackupFileReader.read(stream, data.length + 20));
    }

    @Test public void zeroBulkReadsStillConsumeBytesAndEnforceTheLimit() throws IOException {
        byte[] data = new byte[] { 9, 1, 42 };
        assertArrayEquals(data, BackupFileReader.read(singleByteOnly(data), data.length));
        assertThrows(IllegalStateException.class,
                () -> BackupFileReader.read(singleByteOnly(data), data.length - 1));
    }

    private static InputStream singleByteOnly(byte[] data) {
        return new ByteArrayInputStream(data) {
            private int attempts;

            @Override public int read(byte[] buffer, int offset, int count) {
                if (++attempts > data.length + 1) throw new AssertionError("No read progress");
                return 0;
            }
        };
    }

    @Test public void midstreamReadFailurePropagatesUnchanged() {
        IOException failure = new IOException("local provider closed the stream");
        InputStream stream = new InputStream() {
            private boolean firstRead = true;
            @Override public int read() throws IOException { throw failure; }
            @Override public int read(byte[] buffer, int offset, int count) throws IOException {
                if (!firstRead) throw failure;
                firstRead = false;
                buffer[offset] = 42;
                return 1;
            }
        };
        assertSame(failure, assertThrows(IOException.class, () -> BackupFileReader.read(stream, 10)));
    }

    @Test public void oneByteOverLimitStillRejected() {
        byte[] data = new byte[128 * 1024 + 1];
        assertThrows(IllegalStateException.class, () ->
            BackupFileReader.read(new ByteArrayInputStream(data), data.length - 1));
    }
}
