package io.weave.client.ui

import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.NodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainRatingTest {
    @Test fun scoresRepeatedSuccessAndRejectsIncompleteOrInvalidProbe() {
        val group = CustomProxyGroup(name = "Chain", members = listOf(NodeRef("s", "n")))
        assertNull(rankChainSamples(group, null, 200))
        assertNull(rankChainSamples(group, 100, 0))
        assertEquals(ChainRating(group, 140, 80), rankChainSamples(group, 100, 180))
    }
}
