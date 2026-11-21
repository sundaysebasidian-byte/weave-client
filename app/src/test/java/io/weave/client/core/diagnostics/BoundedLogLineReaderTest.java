package io.weave.client.core.diagnostics;

import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BoundedLogLineReaderTest {
    @Test public void acceptsExactCapAndResumesAfterDiscardedRecord() throws IOException {
        String exact = "x".repeat(8192);
        BoundedLogLineReader reader = new BoundedLogLineReader(
                new StringReader(exact + "\n" + exact + "y".repeat(16384) + "\nnext"), 8192);
        assertEquals(exact, reader.readLine());
        assertEquals("", reader.readLine());
        assertEquals("next", reader.readLine());
        assertNull(reader.readLine());
    }

    @Test public void preservesCrLfAndCrAcrossBulkReadBoundaries() throws IOException {
        StringReader input = new StringReader("a\r\nb\rc\n\n\rd") {
            @Override public int read() { throw new AssertionError("expected a bulk read"); }
            @Override public int read(char[] target, int offset, int length) throws IOException {
                assertTrue(length > 1);
                return super.read(target, offset, 1);
            }
        };
        BoundedLogLineReader reader = new BoundedLogLineReader(input, 16);
        for (String expected : new String[]{"a", "b", "c", "", "", "d"}) {
            assertEquals(expected, reader.readLine());
        }
        assertNull(reader.readLine());
    }

    @Test public void countsUtf16AndDiscardsOverlengthFinalRecordAtEof() throws IOException {
        BoundedLogLineReader reader = new BoundedLogLineReader(
                new StringReader("🚇\r\n🚇x"), 2);
        assertEquals("🚇", reader.readLine());
        assertEquals("", reader.readLine());
        assertNull(reader.readLine());
        assertNull(reader.readLine());
    }
}
