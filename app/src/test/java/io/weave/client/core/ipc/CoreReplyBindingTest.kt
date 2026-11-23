package io.weave.client.core.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class CoreReplyBindingTest {
    @Test fun `detach and rebind ignore the queued messages of the former binding`() {
        val binding = CoreReplyBinding<String>()
        val accepted = mutableListOf<String>()
        fun callback(token: Any, message: String): () -> Unit = {
            if (binding.accepts(token)) accepted += message
        }

        lateinit var lateState: () -> Unit
        lateinit var lateReply: () -> Unit
        binding.replace { token ->
            lateState = callback(token, "first active state")
            lateReply = callback(token, "first late reply")
            "first pair"
        }
        assertEquals("first pair", binding.current)
        lateState()
        binding.clear()
        assertNull(binding.current)
        lateState()
        lateReply()
        lateinit var newState: () -> Unit
        binding.replace { token ->
            newState = callback(token, "second active state")
            "second pair"
        }
        lateState()
        lateReply()
        newState()
        assertEquals("second pair", binding.current)
        assertEquals(listOf("first active state", "second active state"), accepted)
    }

    @Test fun `replacing the same endpoint still starts a distinct binding`() {
        val binding = CoreReplyBinding<String>()
        lateinit var first: Any
        lateinit var second: Any
        binding.replace { first = it; "same pair" }
        binding.replace { second = it; "same pair" }
        assertNotSame(first, second)
        assertEquals(false, binding.accepts(first))
        assertEquals(true, binding.accepts(second))
        assertEquals("same pair", binding.current)
    }
}
