package io.weave.client.subscription;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LocalSubscriptionBufferTest {
    @Test
    public void emptyBufferHasNoTrailingCapacityInTheResult() throws Exception {
        assertEquals("", new LocalSubscriptionBuffer(32 * 1024).decodeUtf8());
    }

    @Test
    public void preservesUtf8IncludingSplitMultibyteBytesAndBom() throws Exception {
        String text = "\ufeff文😀\ufeff";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var output = new LocalSubscriptionBuffer(32 * 1024);
        for (byte part : bytes) output.write(part);

        assertEquals(text, output.decodeUtf8()); // The calling reader, not this buffer, removes a leading BOM.
    }

    @Test
    public void truncatedCharacterAtTheExactWrittenEndIsRejected() throws Exception {
        var output = new LocalSubscriptionBuffer(32 * 1024);
        output.write(0xe4);
        output.write(0xb8);

        try {
            output.decodeUtf8();
            fail("expected the incomplete UTF-8 character to be rejected");
        } catch (CharacterCodingException expected) {
            // Populated bytes end before the last required continuation byte.
        }
    }
}
