package io.weave.client;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Selects an ACTION_SEND payload without treating a shared file name as inline content. */
final class SharedSubscriptionImport {
    private static final String BASE_NAME = "[\\p{L}\\p{M}\\p{N}_.()\\[\\] -]+";
    private static final Pattern YAML_NAME = Pattern.compile(BASE_NAME + "\\.ya?ml", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_NAME = Pattern.compile(BASE_NAME + "\\.json", Pattern.CASE_INSENSITIVE);

    private SharedSubscriptionImport() {}

    static <T> T select(
            String mimeType,
            boolean readGranted,
            String text,
            Function<String, T> parseText,
            Supplier<T> readDocument) {
        if (text == null) return readDocument.get();
        if (readGranted && isDocumentMetadata(mimeType, text.trim())) {
            T document = readDocument.get();
            if (document != null) return document;
        }
        return parseText.apply(text);
    }

    private static boolean isDocumentMetadata(String mimeType, String title) {
        if (mimeType == null) return false;
        return switch (mimeType) {
            case "application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml" ->
                    title.isEmpty() || YAML_NAME.matcher(title).matches();
            case "application/json" -> title.isEmpty() || JSON_NAME.matcher(title).matches();
            default -> false;
        };
    }
}
