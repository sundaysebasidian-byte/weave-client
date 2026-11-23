package io.weave.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SharedSubscriptionImportTest {
    private static final String DOCUMENT = "content://fixture/profile";

    @Test
    public void prefersGrantedDocumentOverMatchingFileName() {
        assertEquals(DOCUMENT, select("application/yaml", true, "校园配置.YAML", DOCUMENT));
        assertEquals(DOCUMENT, select("application/x-yaml", true, "Campus (copy).yml", DOCUMENT));
        assertEquals(DOCUMENT, select("text/yaml", true, "campus.yml", DOCUMENT));
        assertEquals(DOCUMENT, select("text/x-yaml", true, "campus.yml", DOCUMENT));
        assertEquals(DOCUMENT, select("application/json", true, "campus.json", DOCUMENT));
    }

    @Test
    public void prefersGrantedDocumentOverBlankText() {
        assertEquals(DOCUMENT, select("application/json", true, " \n", DOCUMENT));
    }

    @Test
    public void preservesActualSharedContentEvenWithAnAttachment() {
        assertEquals("https://example.test/sub", select("text/plain", true, "https://example.test/sub", DOCUMENT));
        assertEquals("Title\nhttps://example.test/sub",
                select("application/yaml", true, "Title\nhttps://example.test/sub", DOCUMENT));
        assertEquals("proxies: []", select("application/yaml", true, "proxies: []", DOCUMENT));
        assertEquals("{\"outbounds\":[]}", select("application/json", true, "{\"outbounds\":[]}", DOCUMENT));
    }

    @Test
    public void doesNotReadStreamWhenShareIsNotAnExplicitDocument() {
        assertTextWithoutReadingStream("text/plain", true, "campus.yaml");
        assertTextWithoutReadingStream("application/yaml", false, "campus.yaml");
        assertTextWithoutReadingStream("application/json", true, "campus.yaml");
    }

    @Test
    public void preservesTheExistingTextOrStreamFallback() {
        assertEquals(DOCUMENT, select("application/yaml", false, null, DOCUMENT));
        assertEquals("campus.yaml", select("application/yaml", true, "campus.yaml", null));
        assertNull(select("application/yaml", true, " ", null));
    }

    private static void assertTextWithoutReadingStream(String mimeType, boolean readGranted, String text) {
        assertEquals(text, SharedSubscriptionImport.select(mimeType, readGranted, text, value -> value, () -> {
            throw new AssertionError("unexpected stream read");
        }));
    }

    private static String select(String mimeType, boolean readGranted, String text, String document) {
        return SharedSubscriptionImport.select(
                mimeType, readGranted, text, value -> value.trim().isEmpty() ? null : value, () -> document);
    }
}
