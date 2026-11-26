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

    @Test public void httpHandshakeKeepsBufferedEventsAndRejectsInvalidOrTruncatedHeaders() throws IOException {
        BoundedLogLineReader valid = new BoundedLogLineReader(
                new StringReader("HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n\r\n{}\n"), 8192);
        valid.readHttpHeaders();
        assertEquals("{}", valid.readLine());
        assertEquals(204, BoundedLogLineReader.httpStatus("HTTP/1.1 204 No Content"));
        for (String text : new String[]{
                "HTTP/1.0 2000 invalid\r\n\r\n",
                "HTTP/1.1 502 upstream said 200\r\n\r\n",
                "HTTP/1.0 200 OK\r\ntruncated",
                "HTTP/1.0 200 OK\r\n" + "x".repeat(8193) + "\r\n\r\n",
                "HTTP/1.0 200 OK\r\n" + ("x".repeat(7000) + "\r\n").repeat(3) + "\r\n",
        }) {
            BoundedLogLineReader invalid = new BoundedLogLineReader(new StringReader(text), 8192);
            assertThrows(IOException.class, invalid::readHttpHeaders);
        }
    }

    @Test public void completeRowsRemainIndependentWhenChunkIsRefilled() throws IOException {
        String initial = "x".repeat(2047); // CR ends the first chunk; LF starts the next.
        String terminal = "🚇" + "z".repeat(2045); // Straddles a later chunk and has no newline.
        BoundedLogLineReader reader = new BoundedLogLineReader(
                new StringReader(initial + "\r\nnext🚇\n\n" + terminal), 2047);
        String first = reader.readLine();
        String second = reader.readLine();
        assertEquals("", reader.readLine());
        assertEquals(terminal, reader.readLine());
        assertNull(reader.readLine());
        assertEquals(initial, first);
        assertEquals("next🚇", second);
    }

    @Test public void completeRowsInOneChunkHonorLengthCapAndContinueAfterDiscard() throws IOException {
        BoundedLogLineReader reader = new BoundedLogLineReader(
                new StringReader("🚇x\r\n🚇\r\r\nx\n"), 2);
        for (String expected : new String[]{"", "🚇", "", "x"}) {
            assertEquals(expected, reader.readLine());
        }
        assertNull(reader.readLine());
    }
}
