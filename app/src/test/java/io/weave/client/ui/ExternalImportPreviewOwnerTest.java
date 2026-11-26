package io.weave.client.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExternalImportPreviewOwnerTest {
    @Test public void olderSuccessCannotDismissTheNewOffer() {
        String first = ExternalImportPreviewOwner.newId();
        String second = ExternalImportPreviewOwner.newId();
        assertTrue(ExternalImportPreviewOwner.owns(first, first));
        assertFalse(ExternalImportPreviewOwner.owns(second, first));
        assertTrue(ExternalImportPreviewOwner.owns(second, second));
        assertFalse(ExternalImportPreviewOwner.owns(second, null));
    }

    @Test public void olderFailureOrCancellationDoesNotOwnTheNewOffer() {
        String first = ExternalImportPreviewOwner.newId();
        String second = ExternalImportPreviewOwner.newId();
        for (String terminal : new String[]{first, null}) {
            assertFalse(ExternalImportPreviewOwner.owns(second, terminal));
            assertTrue(ExternalImportPreviewOwner.canConfirm(second, second, false, null));
        }
    }

    @Test public void pendingImportAndStaleTapCannotStartDuplicateImport() {
        String first = ExternalImportPreviewOwner.newId();
        String second = ExternalImportPreviewOwner.newId();
        assertTrue(ExternalImportPreviewOwner.canConfirm(first, first, false, null));
        assertFalse(ExternalImportPreviewOwner.canConfirm(first, first, true, null));
        assertFalse(ExternalImportPreviewOwner.canConfirm(first, first, false, first));
        assertTrue(ExternalImportPreviewOwner.canConfirm(second, second, false, first));
        assertFalse(ExternalImportPreviewOwner.canConfirm(second, first, false, null));
        assertFalse(ExternalImportPreviewOwner.canConfirm(second, second, true, null));
        assertFalse(ExternalImportPreviewOwner.canConfirm(null, first, false, null));
    }
    @Test public void newerOfferWaitsWithoutInheritingOlderProgress() {
        String first = ExternalImportPreviewOwner.newId();
        String second = ExternalImportPreviewOwner.newId();
        assertTrue(ExternalImportPreviewOwner.isRunning(first, first, true));
        assertFalse(ExternalImportPreviewOwner.isBlockedByOtherImport(first, first, true));
        assertFalse(ExternalImportPreviewOwner.isRunning(second, first, true));
        assertTrue(ExternalImportPreviewOwner.isBlockedByOtherImport(second, first, true));
        assertFalse(ExternalImportPreviewOwner.isRunning(second, null, true));
        assertTrue(ExternalImportPreviewOwner.isBlockedByOtherImport(second, null, true));
        assertFalse(ExternalImportPreviewOwner.isBlockedByOtherImport(second, first, false));
    }
}
