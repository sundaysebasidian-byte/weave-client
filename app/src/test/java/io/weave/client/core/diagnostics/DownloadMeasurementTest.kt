package io.weave.client.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DownloadMeasurementTest {
    @Test fun oneMiBPerHundredMillisecondsIsDecimalMbps() {
        val m = DownloadMeasurement(1_048_576, 100_000_000, 25_000_000, 123)
        assertEquals(83.88608, m.megabitsPerSecond, 0.000001)
        assertEquals(100L, m.elapsedMillis)
        assertEquals(25L, m.headersMillis)
        assertEquals(75L, m.bodyMillis)
        assertEquals(111.8481066667, m.bodyMegabitsPerSecond!!, 0.000001)
    }

    @Test fun throughputDoesNotUseTruncatedMillis() {
        assertEquals(16.0, DownloadMeasurement(1000, 500_000, 1, 123).megabitsPerSecond, 0.000001)
    }

    @Test fun invalidTimingsCannotYieldAResult() {
        assertThrows(IllegalArgumentException::class.java) { DownloadMeasurement(1, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { DownloadMeasurement(1, 100, 101, 0) }
        assertThrows(IllegalArgumentException::class.java) { DownloadMeasurement(0, 100, 10, 0) }
    }

    @Test fun noBodyIntervalDoesNotProduceAnInfiniteRate() {
        assertNull(DownloadMeasurement(1024, 100, 100, 0).bodyMegabitsPerSecond)
    }
}
