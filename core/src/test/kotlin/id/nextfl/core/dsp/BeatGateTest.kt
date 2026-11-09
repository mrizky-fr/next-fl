package id.nextfl.core.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatGateTest {
    private val rate = 48_000
    private val bpm = 120.0
    private val beat = 24_000
    private val block = 512
    private val level = 0.5f

    // Gate output for steady input
    private fun run(division: Double, frames: Int, start: Long = 0L, gate: BeatGate = BeatGate(rate)): FloatArray {
        val left = FloatArray(frames) { level }
        val right = FloatArray(frames) { level }
        var offset = 0
        while (offset < frames) {
            val count = minOf(block, frames - offset)
            val l = left.copyOfRange(offset, offset + count)
            val r = right.copyOfRange(offset, offset + count)
            gate.process(division, bpm, start + offset, l, r, count)
            l.copyInto(left, offset)
            offset += count
        }
        return left
    }

    @Test
    fun offIsBypass() {
        val input = FloatArray(beat) { level }
        assertContentEquals(input, run(BeatGate.OFF, beat))
    }

    @Test
    fun wholeBeatOpensFirstHalfOnly() {
        val output = run(1.0, beat * 2)
        assertEquals(level, output[beat / 4], 1e-3f)
        assertEquals(0f, output[beat * 3 / 4], 1e-3f)
        assertEquals(level, output[beat + beat / 4], 1e-3f)
        assertEquals(0f, output[beat + beat * 3 / 4], 1e-3f)
    }

    @Test
    fun quarterChopsFourTimesPerBeat() {
        val output = run(0.25, beat)
        val cuts = (1 until output.size).count { output[it - 1] >= level / 2 && output[it] < level / 2 }
        assertEquals(4, cuts)
    }

    @Test
    fun phaseFollowsSongPosition() {
        val output = run(1.0, beat / 4, start = beat / 2L)
        assertEquals(0f, output[beat / 8], 1e-3f)
    }

    @Test
    fun releaseRestoresFullLevel() {
        val gate = BeatGate(rate)
        run(1.0, beat * 3 / 4, gate = gate)
        val after = run(BeatGate.OFF, rate / 10, start = beat * 3 / 4L, gate = gate)
        assertEquals(level, after[rate / 100], 1e-3f)
        assertEquals(level, after.last())
    }

    @Test
    fun edgesDoNotClick() {
        val output = run(0.25, beat * 2)
        val jump = (1 until output.size).maxOf { abs(output[it] - output[it - 1]) }
        assertTrue(jump < level * 0.05f, "edge jump $jump")
    }
}
