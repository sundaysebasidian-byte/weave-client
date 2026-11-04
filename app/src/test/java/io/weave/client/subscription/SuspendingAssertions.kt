package io.weave.client.subscription

// Unlike JUnit's Java SAM, an inline assertion can enclose a suspending operation.
internal inline fun <T : Throwable> assertThrowsSuspending(expected: Class<T>, block: () -> Unit): T =
    assertThrowsSuspending("", expected, block)

internal inline fun <T : Throwable> assertThrowsSuspending(message: String, expected: Class<T>, block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (expected.isInstance(error)) return requireNotNull(expected.cast(error))
        throw AssertionError("$message Expected ${expected.name}, got ${error.javaClass.name}", error)
    }
    throw AssertionError("$message Expected ${expected.name}, but the operation succeeded")
}
