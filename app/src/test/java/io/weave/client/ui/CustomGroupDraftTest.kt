package io.weave.client.ui

import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGroupDraftTest {
    private val entry = NodeRef("first", "shared-node-id")
    private val otherProvider = NodeRef("second", "shared-node-id")
    private val otherMember = NodeRef("first", "other-node-id")
    private val groupId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    @Test fun `re-enabling chaining removes only the retained exact entry before save`() {
        val original = linkedSetOf(otherProvider, otherMember)
        val disabled = customGroupDraftMembers(original, entry, chained = false)
        assertSame(original, disabled)
        val selectedWhileDisabled = disabled + entry + otherMember
        assertEquals(listOf(otherProvider, otherMember, entry), selectedWhileDisabled.toList())
        assertTrue(runCatching {
            CustomProxyGroupValidator.normalize(
                CustomProxyGroup(groupId, "Example", members = selectedWhileDisabled.toList(), entry = entry),
            )
        }.isFailure)

        val enabled = customGroupDraftMembers(selectedWhileDisabled, entry, chained = true)
        val saved = CustomProxyGroupValidator.normalize(
            CustomProxyGroup(groupId, "Example", members = enabled.toList(), entry = entry),
        )
        assertEquals(original.toList(), saved.members)
        assertEquals(entry, saved.entry)
    }

    @Test fun `disabling or toggling without a role conflict preserves the existing draft`() {
        val members = linkedSetOf(otherMember)
        assertSame(members, customGroupDraftMembers(members, entry, chained = false))
        assertSame(members, customGroupDraftMembers(members, entry, chained = true))
        assertSame(members, customGroupDraftMembers(members, null, chained = true))
        val withEntry = linkedSetOf(entry, otherMember)
        assertSame(withEntry, customGroupDraftMembers(withEntry, entry, chained = false))
    }

    @Test fun `a sole member becoming the entry leaves no exit member`() {
        assertTrue(customGroupDraftMembers(setOf(entry), entry, chained = true).isEmpty())
    }
}
