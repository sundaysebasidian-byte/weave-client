package io.weave.client.core.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BundledGeodataStagingTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void removesPartiallyWrittenFileAndPreservesActiveData() throws Exception {
        Path home = folder.getRoot().toPath();
        Path active = Files.writeString(home.resolve("GeoIP.dat"), "previous data");
        Path pending = home.resolve("GeoIP.dat.pending");
        IOException interrupted = new IOException("fixture asset read interrupted");

        try {
            BundledGeodataStaging.run(pending.toFile(), () -> {
                Files.writeString(pending, "partially copied");
                throw interrupted;
            });
            fail("expected the original copy failure");
        } catch (IOException actual) {
            assertSame(interrupted, actual);
        }

        assertFalse(Files.exists(pending));
        assertEquals("previous data", Files.readString(active));
    }

    @Test
    public void replacesAStaleRegularStageBeforePromotingVerifiedBytes() throws Exception {
        Path home = folder.getRoot().toPath();
        Path active = home.resolve("GeoIP.dat");
        Path pending = Files.writeString(home.resolve("GeoIP.dat.pending"), "from previous attempt");

        BundledGeodataStaging.run(pending.toFile(), () -> {
            assertTrue(!Files.exists(pending) || Files.size(pending) == 0L);
            Files.writeString(pending, "fixture verified data");
            Files.move(pending, active);
        });

        assertFalse(Files.exists(pending));
        assertEquals("fixture verified data", Files.readString(active));
    }

    @Test
    public void stillRecoversFromAnEmptyLegacyStageDirectory() throws Exception {
        Path home = folder.getRoot().toPath();
        Path active = home.resolve("GeoIP.dat");
        Path pending = Files.createDirectory(home.resolve("GeoIP.dat.pending"));

        BundledGeodataStaging.run(pending.toFile(), () -> {
            Files.writeString(pending, "fixture verified data");
            Files.move(pending, active);
        });

        assertFalse(Files.exists(pending));
        assertEquals("fixture verified data", Files.readString(active));
    }
}
