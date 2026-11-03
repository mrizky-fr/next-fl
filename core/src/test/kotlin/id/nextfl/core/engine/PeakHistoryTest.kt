package id.nextfl.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PeakHistoryTest {
    @Test
    fun returnsPeakOfBlockHoldingFrame() {
        val history = PeakHistory(capacity = 4)
        history.write(0, 100, 0.1f)
        history.write(100, 100, 0.5f)
        assertEquals(0.1f, history.at(99))
        assertEquals(0.5f, history.at(100))
        assertEquals(0f, history.at(200), "future frame not written yet")
    }

    @Test
    fun forgetsBlocksOlderThanCapacity() {
        val history = PeakHistory(capacity = 2)
        history.write(0, 10, 0.9f)
        history.write(10, 10, 0.2f)
        history.write(20, 10, 0.3f)
        assertEquals(0f, history.at(5))
        assertEquals(0.3f, history.at(25))
    }

    @Test
    fun resetDropsOldClock() {
        val history = PeakHistory()
        history.write(0, 10, 0.7f)
        history.reset()
        assertEquals(0f, history.at(5))
    }
}
