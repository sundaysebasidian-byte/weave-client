package io.weave.client.core.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeApkPayloadProbeTest {
    private static final String[] SUPPORTED = {"arm64-v8a", "armeabi-v7a"};

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void opensEachApkOnlyOnceWhenASecondaryAbiIsInASplit() throws IOException {
        String base = zip("base.apk", "AndroidManifest.xml");
        String split = zip("split.apk", "lib/armeabi-v7a/libbridge.so", "lib/armeabi-v7a/libclash.so");
        var opened = new ArrayList<String>();

        assertTrue(NativeApkPayloadProbe.containsPair(List.of(base, split), SUPPORTED, path -> {
            opened.add(path);
            return new ZipFile(path);
        }));
        assertEquals(List.of(base, split), opened);
    }

    @Test
    public void cannotCombineLibrariesAcrossApksOrAbis() throws IOException {
        String base = zip("base.apk", "lib/arm64-v8a/libbridge.so", "lib/armeabi-v7a/libclash.so");
        String split = zip("split.apk", "lib/arm64-v8a/libclash.so",
                "lib/x86/libbridge.so", "lib/x86/libclash.so");

        assertFalse(NativeApkPayloadProbe.containsPair(List.of(base, split), SUPPORTED));
    }

    @Test
    public void skipsUnreadableSourceAndStillDetectsThePair() throws IOException {
        String unreadable = Files.writeString(folder.getRoot().toPath().resolve("broken.apk"), "not a ZIP").toString();
        String split = zip("split.apk", "lib/arm64-v8a/libbridge.so", "lib/arm64-v8a/libclash.so");

        assertTrue(NativeApkPayloadProbe.containsPair(List.of(unreadable, split), SUPPORTED));
        assertFalse(NativeApkPayloadProbe.containsPair(List.of(unreadable), SUPPORTED));
    }

    @Test
    public void nextAbiStillWorksIfOneEntryLookupFails() throws IOException {
        String split = zip("split.apk", "lib/armeabi-v7a/libbridge.so", "lib/armeabi-v7a/libclash.so");

        assertTrue(NativeApkPayloadProbe.containsPair(List.of(split), SUPPORTED, path -> new ZipFile(path) {
            @Override public ZipEntry getEntry(String name) {
                if (name.equals("lib/arm64-v8a/libbridge.so")) {
                    throw new IllegalArgumentException("fixture lookup failure");
                }
                return super.getEntry(name);
            }
        }));
    }

    @Test
    public void noSupportedAbiDoesNotOpenAnArchive() {
        assertFalse(NativeApkPayloadProbe.containsPair(List.of("fixture.apk"), new String[0], path -> {
            throw new AssertionError("unexpected archive open");
        }));
    }

    private String zip(String name, String... entries) throws IOException {
        var file = folder.getRoot().toPath().resolve(name);
        try (var output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String nameInZip : entries) {
                output.putNextEntry(new ZipEntry(nameInZip));
                output.closeEntry();
            }
        }
        return file.toString();
    }
}
