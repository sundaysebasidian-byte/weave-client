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
    private static final int ROW_COUNT = 1467;
    private ReleaseCopyCatalog() {}

    static String assetPath(String languageTag) {
        return PackagedReleaseCopyAsset.assetPath(languageTag);
    }

    static Map<String, String> loadVerified(String languageTag, InputStream input) throws IOException {
        PackagedReleaseCopyAsset.Seal seal = PackagedReleaseCopyAsset.sealFor(languageTag);
        if (seal == null) throw new IOException("unregistered app-copy locale");
        byte[] source = new byte[seal.bytes];
        int offset = 0;
        while (offset < source.length) {
            int count = input.read(source, offset, source.length - offset);
            if (count <= 0) throw new IOException("incomplete app-copy catalog stream");
            offset += count;
        }
        if (input.read() != -1 || !seal.sha256.equals(sha256(source))) {
            throw new IOException("app-copy catalog does not match its package seal");
        }

        Map<String, String> entries = new LinkedHashMap<>();
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

    private static String sha256(byte[] source) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(source);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte part : digest) {
            hex.append(Character.forDigit((part & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(part & 15, 16));
        }
        return hex.toString();
    }
}
