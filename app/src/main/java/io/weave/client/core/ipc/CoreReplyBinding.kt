package io.weave.client.core.ipc

/** Binding updates and callback delivery are Main-owned. Senders read one complete reply pair. */
internal class CoreReplyBinding<T> {
    private class Session<T>(val token: Any, val endpoint: T)

    @Volatile private var active: Session<T>? = null
    val current: T? get() = active?.endpoint

    fun replace(create: (Any) -> T) {
        val token = Any()
        active = Session(token, create(token))
    }

    fun clear() {
        active = null
    }

    fun accepts(token: Any): Boolean = active?.token === token
}
