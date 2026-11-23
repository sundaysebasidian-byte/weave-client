package io.weave.client.ui

import io.weave.client.domain.SubscriptionSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionSourceDispatchTest {
    @Test fun explicitPendingSourceOpensOnlyOnceEvenAfterRemount() {
        var pending: SubscriptionSourceKind? = SubscriptionSourceKind.QR_CODE
        val opened = mutableListOf<SubscriptionSourceKind>()
        fun consume(source: SubscriptionSourceKind): Boolean {
            if (pending != source) return false
            pending = null
            return true
        }
        fun dispatch(request: SubscriptionSourceKind?) = dispatchSubscriptionSource(
            request, false, ::consume,
            { opened += SubscriptionSourceKind.QR_CODE },
            { opened += SubscriptionSourceKind.LOCAL_FILE },
        )

        dispatch(pending)
        dispatch(SubscriptionSourceKind.QR_CODE)
        dispatch(pending)
        assertNull(pending)
        assertEquals(listOf(SubscriptionSourceKind.QR_CODE), opened)
    }

    @Test fun busyOrSupersededRequestCannotOpenAPicker() {
        var pending: SubscriptionSourceKind? = SubscriptionSourceKind.LOCAL_FILE
        val opened = mutableListOf<SubscriptionSourceKind>()
        fun consume(source: SubscriptionSourceKind): Boolean {
            if (pending != source) return false
            pending = null
            return true
        }
        fun dispatch(request: SubscriptionSourceKind?, busy: Boolean) = dispatchSubscriptionSource(
            request, busy, ::consume,
            { opened += SubscriptionSourceKind.QR_CODE },
            { opened += SubscriptionSourceKind.LOCAL_FILE },
        )

        dispatch(SubscriptionSourceKind.QR_CODE, false)
        assertEquals(SubscriptionSourceKind.LOCAL_FILE, pending)
        dispatch(pending, true)
        dispatch(SubscriptionSourceKind.LOCAL_FILE, false)
        assertNull(pending)
        assertEquals(emptyList<SubscriptionSourceKind>(), opened)

        pending = SubscriptionSourceKind.LOCAL_FILE
        dispatch(pending, false)
        assertNull(pending)
        assertEquals(listOf(SubscriptionSourceKind.LOCAL_FILE), opened)
    }
}
