package io.weave.client.ui

import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Subscription list filtering and expiry wording inputs. Pure data; no Compose, clock or network. */
class SubscriptionLibraryModelTest {
    private val week = 7L * 24 * 60 * 60 * 1000
    private val now = 1_000_000_000_000L

    private fun sub(id: String, name: String, remote: Boolean) = Subscription(id, name, nodeCount = 3, remote = remote)

    private val work = sub("a", "Work Proxy", remote = true)
    private val home = sub("b", "  home 本地  ", remote = false)
    private val backup = sub("c", "WORK backup", remote = false)
    private val all = listOf(work, home, backup)

    private fun ids(list: List<Subscription>) = list.map { it.id }

    @Test
    fun `source filter selects all remote or local and keeps order`() {
        assertEquals(listOf("a", "b", "c"), ids(filterSubscriptions(all, "", SubscriptionSourceFilter.ALL)))
        assertEquals(listOf("a"), ids(filterSubscriptions(all, "", SubscriptionSourceFilter.REMOTE)))
        assertEquals(listOf("b", "c"), ids(filterSubscriptions(all, "", SubscriptionSourceFilter.LOCAL)))
    }

    @Test
    fun `search ignores case and surrounding whitespace`() {
        assertEquals(listOf("a", "c"), ids(filterSubscriptions(all, "  wOrK \n", SubscriptionSourceFilter.ALL)))
        assertEquals(listOf("b"), ids(filterSubscriptions(all, "本地", SubscriptionSourceFilter.ALL)))
        assertEquals(emptyList<Subscription>(), filterSubscriptions(all, "missing", SubscriptionSourceFilter.ALL))
    }

    @Test
    fun `blank query keeps every name and the source filter still applies`() {
        listOf("", "   ", "\t\n").forEach { query ->
            assertEquals(ids(all), ids(filterSubscriptions(all, query, SubscriptionSourceFilter.ALL)))
            assertEquals(listOf("b", "c"), ids(filterSubscriptions(all, query, SubscriptionSourceFilter.LOCAL)))
        }
        assertEquals(emptyList<Subscription>(), filterSubscriptions(emptyList(), "work", SubscriptionSourceFilter.ALL))
    }

    @Test
    fun `query and source combine`() {
        assertEquals(listOf("c"), ids(filterSubscriptions(all, "work", SubscriptionSourceFilter.LOCAL)))
        assertEquals(listOf("a"), ids(filterSubscriptions(all, "work", SubscriptionSourceFilter.REMOTE)))
        assertEquals(emptyList<Subscription>(), filterSubscriptions(all, "home", SubscriptionSourceFilter.REMOTE))
    }

    @Test
    fun `results are the original objects with names and values untouched`() {
        val result = filterSubscriptions(all, "home", SubscriptionSourceFilter.ALL)
        assertEquals(1, result.size)
        assertSame(home, result.single())
        // The user's own padding and casing are never normalised in the data.
        assertEquals("  home 本地  ", result.single().name)
        assertSame(all[0], filterSubscriptions(all, "", SubscriptionSourceFilter.ALL)[0])
    }

    @Test
    fun `missing nonpositive or unknown expiry is unknown`() {
        assertEquals(SubscriptionExpiryStatus.UNKNOWN, subscriptionExpiryStatus(null, now))
        assertEquals(SubscriptionExpiryStatus.UNKNOWN, subscriptionExpiryStatus(0L, now))
        assertEquals(SubscriptionExpiryStatus.UNKNOWN, subscriptionExpiryStatus(-1L, now))
        assertEquals(SubscriptionExpiryStatus.UNKNOWN, subscriptionExpiryStatus(Long.MIN_VALUE, now))
    }

    @Test
    fun `expiry at or before now is expired`() {
        assertEquals(SubscriptionExpiryStatus.EXPIRED, subscriptionExpiryStatus(now, now))
        assertEquals(SubscriptionExpiryStatus.EXPIRED, subscriptionExpiryStatus(now - 1, now))
        assertEquals(SubscriptionExpiryStatus.EXPIRED, subscriptionExpiryStatus(1L, now))
    }

    @Test
    fun `seven days inclusive is expiring soon and beyond is active`() {
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, subscriptionExpiryStatus(now + 1, now))
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, subscriptionExpiryStatus(now + week - 1, now))
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, subscriptionExpiryStatus(now + week, now))
        assertEquals(SubscriptionExpiryStatus.ACTIVE, subscriptionExpiryStatus(now + week + 1, now))
        assertEquals(SubscriptionExpiryStatus.ACTIVE, subscriptionExpiryStatus(Long.MAX_VALUE, now))
    }

    @Test
    fun `long boundaries do not overflow`() {
        assertEquals(SubscriptionExpiryStatus.EXPIRED, subscriptionExpiryStatus(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, subscriptionExpiryStatus(Long.MAX_VALUE, Long.MAX_VALUE - 1))
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, subscriptionExpiryStatus(Long.MAX_VALUE, Long.MAX_VALUE - week))
        assertEquals(SubscriptionExpiryStatus.ACTIVE, subscriptionExpiryStatus(Long.MAX_VALUE, Long.MAX_VALUE - week - 1))
        // A hugely negative clock must not wrap the distance into the past.
        assertEquals(SubscriptionExpiryStatus.ACTIVE, subscriptionExpiryStatus(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(SubscriptionExpiryStatus.ACTIVE, subscriptionExpiryStatus(1L, Long.MIN_VALUE))
        assertEquals(SubscriptionExpiryStatus.EXPIRED, subscriptionExpiryStatus(1L, Long.MAX_VALUE))
    }

    @Test
    fun `expiry only quota with zero total is still evaluated from its expiry`() {
        fun status(expireAt: Long?) = subscriptionExpiryStatus(
            Subscription("q", "Quota", 1, quota = SubscriptionQuota(0, 0, expireAt)).quota?.expireAtMillis,
            now,
        )
        assertEquals(SubscriptionExpiryStatus.EXPIRED, status(now - 1))
        assertEquals(SubscriptionExpiryStatus.EXPIRING_SOON, status(now + week))
        assertEquals(SubscriptionExpiryStatus.ACTIVE, status(now + week + 1))
        assertEquals(SubscriptionExpiryStatus.UNKNOWN, status(null))
    }
}
