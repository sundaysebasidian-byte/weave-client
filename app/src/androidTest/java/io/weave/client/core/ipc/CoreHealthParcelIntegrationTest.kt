package io.weave.client.core.ipc

import android.os.Parcel
import io.weave.client.core.engine.NodeHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Framework codec only: no service bind, tunnel, network or personal data. */
class CoreHealthParcelIntegrationTest {
    @Test fun fiveThousandSyntheticHealthRowsFitWithRoomAndRoundTrip() {
        val rows = List(5_000) { i ->
            NodeHealthSnapshot("服务节点 Tokyo $i", "Trojan", if (i % 3 == 1) null else 600 + i % 120,
                3, if (i % 3 == 1) 0 else 2, i % 15, if (i % 3 == 1) 100 else 33,
                if (i % 3 == 1) null else 657 + i % 71)
        }
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(CoreIpc.encodeHealth(rows))
            val bytes = parcel.dataSize()
            assertTrue("Synthetic health parcel has $bytes bytes", bytes < 512 * 1024)
            parcel.setDataPosition(0)
            assertEquals(rows, CoreIpc.decodeHealth(checkNotNull(parcel.readBundle(javaClass.classLoader))))
        } finally {
            parcel.recycle()
        }
    }
}
