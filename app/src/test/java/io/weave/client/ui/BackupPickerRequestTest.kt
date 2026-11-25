package io.weave.client.ui

import io.weave.client.ui.BackupPickerRequest.Kind.EXPORT
import io.weave.client.ui.BackupPickerRequest.Kind.IMPORT
import org.junit.Assert.*
import org.junit.Test

class BackupPickerRequestTest {
    @Test fun onceOnlyOwnershipIgnoresOtherPickerAndRedeliveredResults() {
        val picker = BackupPickerRequest()
        var launched = 0
        picker.start(EXPORT, "Original fixture") { launched++ }
        picker.start(IMPORT, "Not the original") { launched++ }
        assertEquals(1, launched)
        assertNull(picker.consume(IMPORT, true))
        assertTrue(picker.active)
        val key = picker.consume(EXPORT, true)
        assertArrayEquals("Original fixture".toCharArray(), key)
        key!!.fill('\u0000')
        assertNull(picker.consume(EXPORT, true))
    }

    @Test fun noDocumentOrDisposalDoesNotHandOffAnEmptyPassword() {
        val picker = BackupPickerRequest()
        picker.start(EXPORT, "Fixture password") {}
        assertNull(picker.consume(EXPORT, false))
        assertFalse(picker.active)
        picker.start(IMPORT, "Do not persist") {}
        picker.close()
        assertNull(picker.consume(IMPORT, true))
        assertFalse(picker.active)
    }

    @Test fun launchFailureReleasesOwnerSoRetryWorks() {
        val picker = BackupPickerRequest()
        assertThrows(IllegalStateException::class.java) {
            picker.start(EXPORT, "Fixture original") { error("picker not provided") }
        }
        assertFalse(picker.active)
        picker.start(IMPORT, "Fresh fixture") {}
        val key = picker.consume(IMPORT, true)
        assertArrayEquals("Fresh fixture".toCharArray(), key)
        key!!.fill('\u0000')
    }
}
