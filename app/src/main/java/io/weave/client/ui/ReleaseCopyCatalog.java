package io.weave.client.ui;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Admission for package-owned app-copy catalogs; loading does not choose or persist a locale. */
final class ReleaseCopyCatalog {
    private static final int ROW_COUNT = 1530;
    private ReleaseCopyCatalog() {}

    static String assetPath(String languageTag) {
        return PackagedReleaseCopyAsset.assetPath(languageTag);
    }

    static Map<String, String> loadVerified(String languageTag, InputStream input) throws IOException {
        byte[] source = readSealed(languageTag, input, true);

        Map<String, String> entries = new LinkedHashMap<>(ROW_COUNT * 4 / 3 + 1);
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                JSONObject row = new JSONObject(line);
                if (row.length() != 2 || !(row.get("id") instanceof String id)
                        || !(row.get("text") instanceof String copy)
                        || id.trim().isEmpty() || copy.trim().isEmpty() || entries.putIfAbsent(id, copy) != null) {
                    throw new IOException("invalid app-copy catalog row");
                }
            }
        } catch (JSONException error) {
            throw new IOException("invalid app-copy catalog JSON", error);
        }
        if (entries.size() != ROW_COUNT) throw new IOException("incomplete app-copy catalog");
        return Collections.unmodifiableMap(entries);
    }

    static Boolean verifySeal(String languageTag, InputStream input) throws IOException {
        readSealed(languageTag, input, false);
        return true;
    }

    private static byte[] readSealed(String languageTag, InputStream input, boolean retain) throws IOException {
        PackagedReleaseCopyAsset.Seal seal = PackagedReleaseCopyAsset.sealFor(languageTag);
        if (seal == null) throw new IOException("unregistered app-copy locale");
        byte[] source = new byte[retain ? seal.bytes : Math.min(seal.bytes, 16 * 1024)];
        MessageDigest hash;
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        int remaining = seal.bytes;
        while (remaining > 0) {
            int offset = retain ? seal.bytes - remaining : 0;
            int count = input.read(source, offset, Math.min(source.length - offset, remaining));
            if (count == 0) {
                int single = input.read();
                if (single < 0) throw new IOException("incomplete app-copy catalog stream");
                source[offset] = (byte) single;
                count = 1;
            }
            if (count < 0) throw new IOException("incomplete app-copy catalog stream");
            hash.update(source, offset, count);
            remaining -= count;
        }
        if (input.read() != -1 || !seal.sha256.equals(hex(hash.digest()))) {
            throw new IOException("app-copy catalog does not match its package seal");
        }
        return source;
    }

    private static String hex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte part : digest) {
            hex.append(Character.forDigit((part & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(part & 15, 16));
        }
        return hex.toString();
    }
}
