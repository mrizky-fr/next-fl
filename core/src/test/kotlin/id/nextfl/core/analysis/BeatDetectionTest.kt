package id.nextfl.core.analysis

import id.nextfl.core.TestSignals
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatDetectionTest {
    private val rate = 44_100
    private val hop = 735

    private class Run(val beats: Int, val maxStrength: Float)

    private fun detect(signal: FloatArray, bass: Float = 1f, mid: Float = 0f, high: Float = 0f): Run {
        val analyzer = SpectrumAnalyzer(rate)
        val window = FloatArray(analyzer.fftSize)
        var beats = 0
        var strongest = 0f
        var start = 0
        while (start + window.size < signal.size) {
            signal.copyInto(window, 0, start, start + window.size)
            val frame = analyzer.analyze(window, hop.toFloat() / rate, 1f, bass, mid, high)
            if (frame.beat) {
                beats++
                strongest = max(strongest, frame.beatStrength)
            }
            start += hop
        }
        return Run(beats, strongest)
    }

    // Kick every gap seconds
    private fun kicks(gapSeconds: Double, seconds: Double): FloatArray {
        val data = FloatArray((seconds * rate).toInt())
        val length = (rate * 0.2).toInt()
        var start = 0.0
        while (start < data.size) {
            for (i in 0 until length) {
                val at = start.toInt() + i
                if (at >= data.size) break
                val t = i.toDouble() / rate
                data[at] += (sin(2 * PI * 55 * t) * exp(-t * 25)).toFloat()
            }
            start += gapSeconds * rate
        }
        return data
    }

    @Test
    fun rapidKicksRetriggerEachHit() {
        val beats = detect(kicks(0.117, 2.0)).beats
        assertTrue(beats >= 12, "beats $beats of 17 hits")
    }

    @Test
    fun steadyToneIsNotABeat() {
        val beats = detect(TestSignals.sine(55.0, 2.0, rate, 0.8f)).beats
        assertTrue(beats <= 1, "beats $beats")
    }

    @Test
    fun highWeightAddsHats() {
        val loop = TestSignals.drumLoop(120.0, 4.0, rate)
        val bassOnly = detect(loop).beats
        val withHats = detect(loop, high = 1f).beats
        assertTrue(bassOnly in 6..9, "bass beats $bassOnly")
        assertTrue(withHats >= 13, "kick plus hat beats $withHats")
    }

    @Test
    fun weightScalesStrength() {
        val loop = TestSignals.drumLoop(120.0, 4.0, rate)
        val half = detect(loop, bass = 0.5f)
        assertTrue(half.beats > 0)
        assertTrue(half.maxStrength <= 0.5f, "strength ${half.maxStrength}")
        assertEquals(0, detect(loop, bass = 0f).beats)
    }
}
