package io.weave.client.ui

import org.junit.Assert.*
import org.junit.Test

class LanImportDraftTest {
    @Test fun `manually cleared scan stays cleared and cannot reuse its old code`() {
        val withCode = LanImportDraft().acceptValidatedScan(1, "fixture A").editCode("123456")
        assertTrue(withCode.canImport)
        val cleared = withCode.editLink("").acceptValidatedScan(1, "fixture A")
        assertEquals("", cleared.link)
        assertEquals("", cleared.confirmationCode)
        assertFalse(cleared.canImport)
    }

    @Test fun `fresh validated scan takes ownership even when its decoded value repeats`() {
        val scanned = LanImportDraft().acceptValidatedScan(1, "fixture A").editCode("123456")
        val manual = scanned.editLink("draft B").editCode("654321")
        val rescanned = manual.acceptValidatedScan(2, "fixture A")
        assertEquals("fixture A", rescanned.link)
        assertEquals("", rescanned.confirmationCode)
        val withNewCode = rescanned.editCode("987654")
        assertEquals("", withNewCode.acceptValidatedScan(3, "fixture A").confirmationCode)
        val otherSender = withNewCode.acceptValidatedScan(4, "fixture C")
        assertEquals("fixture C", otherSender.link)
        assertFalse(otherSender.canImport)
    }

    @Test fun `a newer live scan vetoes a stale submit but reset and matching revisions do not`() {
        val oldDraft = LanImportDraft().acceptValidatedScan(1, "fixture A").editCode("123456")
        val latest = oldDraft.acceptValidatedScan(2, "fixture B").editCode("654321")
        assertFalse(canSubmitLanImport(oldDraft.lastScanRevision, 2))
        assertTrue(canSubmitLanImport(latest.lastScanRevision, 2))
        assertTrue(canSubmitLanImport(oldDraft.lastScanRevision, 0))
        assertTrue(canSubmitLanImport(LanImportDraft().lastScanRevision, 0))
        assertFalse(canSubmitLanImport(LanImportDraft().lastScanRevision, 1))
    }

    @Test fun `ordinary state changes and older tickets keep the live manual draft`() {
        val draft = LanImportDraft().acceptValidatedScan(3, "fixture A")
            .editLink("fixture B").editCode("12x34567")
        assertEquals("123456", draft.confirmationCode)
        assertSame(draft, draft.acceptValidatedScan(0, ""))
        assertSame(draft, draft.acceptValidatedScan(2, "obsolete"))
        assertSame(draft, draft.editLink("fixture B"))
        assertEquals("", draft.editLink("fixture C").confirmationCode)
        assertEquals(2_048, draft.editLink("x".repeat(2_049)).link.length)
    }
}
