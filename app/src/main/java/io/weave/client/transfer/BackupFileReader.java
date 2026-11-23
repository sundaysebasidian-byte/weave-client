package io.weave.client.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Loads an encrypted backup without a geometrically growing contiguous buffer. */
public final class BackupFileReader {
    private static final int CHUNK_SIZE = 64 * 1024;

    private BackupFileReader() {}

    public static byte[] read(InputStream input, int limit) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        int size = 0;
        while (size < limit) {
            byte[] chunk = new byte[Math.min(CHUNK_SIZE, limit - size)];
            int used = 0;
            while (used < chunk.length) {
                int count = input.read(chunk, used, chunk.length - used);
                if (count < 0) break;
                used += count;
            }
            if (used == 0) break;
            chunks.add(chunk);
            size += used;
            if (used < chunk.length) break;
        }
        if (size == limit && input.read() != -1) {
            throw new IllegalStateException("备份文件过大");
        }
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            int count = Math.min(chunk.length, size - offset);
            System.arraycopy(chunk, 0, result, offset, count);
            offset += count;
        }
        return result;
    }
}
