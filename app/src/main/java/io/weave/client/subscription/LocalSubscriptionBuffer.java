package io.weave.client.subscription;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Decodes exactly the populated bytes without copying the complete subscription again. */
final class LocalSubscriptionBuffer extends ByteArrayOutputStream {
    LocalSubscriptionBuffer(int initialBytes) {
        super(initialBytes);
    }

    synchronized String decodeUtf8() throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buf, 0, count))
                .toString();
    }
}
