package io.weave.client.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedUtf8Test {
    @Test fun countsMixedUtf8AtTheExactBoundary() {
        val value = "a·مرحبا节点📱"
        val size = value.toByteArray(Charsets.UTF_8).size
        assertEquals(size, utf8SizeUpTo(value, size))
        assertNull(utf8SizeUpTo(value, size - 1))
        assertNull(utf8SizeUpTo("a\uD800x", 200))
        assertNull(utf8SizeUpTo("a\uDC00x", 200))
        assertEquals(0, utf8SizeUpTo("", 0))
    }
}
