package io.weave.client.ui

/** Ephemeral form values; a scan ticket never stores or supplies the confirmation code. */
internal class LanImportDraft(
    val link: String = "",
    val confirmationCode: String = "",
    val lastScanRevision: Long = 0L,
) {
    val canImport: Boolean get() = link.isNotBlank() && confirmationCode.length == 6

    fun editLink(value: String): LanImportDraft {
        val next = value.take(2_048)
        return if (next == link) this else LanImportDraft(next, lastScanRevision = lastScanRevision)
    }

    fun editCode(value: String): LanImportDraft {
        val next = value.filter(Char::isDigit).take(6)
        return if (next == confirmationCode) this else LanImportDraft(link, next, lastScanRevision)
    }

    fun acceptValidatedScan(revision: Long, value: String): LanImportDraft =
        if (revision <= lastScanRevision || value.isBlank()) this
        else LanImportDraft(value, lastScanRevision = revision)
}

/** No newly validated scan may replace the draft between composition and submission. */
internal fun canSubmitLanImport(observedRevision: Long, pendingRevision: Long): Boolean =
    pendingRevision <= observedRevision
