package io.weave.client.subscription

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Both Base64 alphabets admit missing padding. Never repair malformed UTF-8 into credentials. */
internal fun decodeSubscriptionEnvelope(compact: String): String {
    val decoder = if ('-' in compact || '_' in compact) Base64.getUrlDecoder() else Base64.getDecoder()
    val remainder = compact.length % 4
    val encoded = if (remainder != 0 && compact.endsWith('=')) {
        compact.padEnd(compact.length + 4 - remainder, '=')
    } else compact
    val bytes = decoder.decode(encoded)
    if (bytes.all { it >= 0 }) return String(bytes, StandardCharsets.US_ASCII)
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
}
