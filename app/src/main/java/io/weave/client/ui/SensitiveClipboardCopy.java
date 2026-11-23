package io.weave.client.ui;

import java.util.UUID;

/** Identifies this copy operation, never the text it placed on the clipboard. */
final class SensitiveClipboardCopy {
    static final String EXTRA_COPY_TOKEN = "io.weave.client.clipboard.COPY_TOKEN";

    private final String token = UUID.randomUUID().toString();

    String token() {
        return token;
    }

    boolean owns(String currentToken) {
        return token.equals(currentToken);
    }
}
