package io.weave.client.ui

/**
 * A picker owns one volatile password snapshot. Android may redeliver a result after a screen
 * was recreated; without the original snapshot that result must never start a file operation.
 */
internal class BackupPickerRequest : AutoCloseable {
    enum class Kind { EXPORT, IMPORT }

    private class Pending(val kind: Kind, val key: CharArray)
    private var pending: Pending? = null
    val active get() = pending != null

    fun start(kind: Kind, password: String, launch: () -> Unit) {
        if (active) return
        pending = Pending(kind, password.toCharArray())
        try {
            launch()
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    fun consume(kind: Kind, destinationChosen: Boolean): CharArray? {
        val current = pending ?: return null
        if (current.kind != kind) return null
        pending = null
        if (destinationChosen) return current.key // Ownership moves to the file operation.
        current.key.fill('\u0000')
        return null
    }

    override fun close() {
        pending?.key?.fill('\u0000')
        pending = null
    }
}
