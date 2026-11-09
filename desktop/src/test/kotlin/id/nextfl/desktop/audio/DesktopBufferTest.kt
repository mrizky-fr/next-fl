package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopBufferTest {
    private val block = AudioStream.BLOCK_FRAMES

    @Test
    fun startQueueCoversDirectSoundLatency() {
        val frames = DesktopBuffer.startFrames(RATE)
        assertTrue(frames >= RATE / 10, "only $frames frames queued")
        assertEquals(0, frames % block)
    }

    @Test
    fun nearlyEmptyQueueGrowsBase() {
        assertEquals(block * 6, DesktopBuffer.grownBase(block * 5, queued = block - 1L, capacity = block * 32))
    }

    @Test
    fun healthyQueueKeepsBase() {
        assertEquals(block * 5, DesktopBuffer.grownBase(block * 5, queued = block * 3L, capacity = block * 32))
    }

    @Test
    fun growthStopsAtCapacity() {
        assertEquals(block * 32, DesktopBuffer.grownBase(block * 32, queued = 0L, capacity = block * 32))
    }

    private companion object {
        const val RATE = 44_100
    }
}
