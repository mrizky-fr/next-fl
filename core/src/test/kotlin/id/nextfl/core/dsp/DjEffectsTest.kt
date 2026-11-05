package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.DjSettings
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DjEffectsTest {
    private val rate = 44_100
    private val block = 512

    private fun run(settings: DjSettings, input: FloatArray, bpm: Double = 120.0): FloatArray {
        val fx = DjEffects(rate)
        val left = input.copyOf()
        val right = input.copyOf()
        var offset = 0
        while (offset < left.size) {
            val count = minOf(block, left.size - offset)
            val l = left.copyOfRange(offset, offset + count)
            val r = right.copyOfRange(offset, offset + count)
            fx.process(settings, bpm, l, r, count)
            l.copyInto(left, offset)
            offset += count
        }
        return left
    }

    private fun rms(data: FloatArray, from: Int = rate / 4, to: Int = data.size) =
        sqrt((from until to).sumOf { (data[it] * data[it]).toDouble() } / (to - from))

    @Test
    fun allZeroIsBypass() {
        val input = TestSignals.sine(440.0, 1.0, rate, 0.4f)
        assertContentEquals(input, run(DjSettings(), input))
    }

    @Test
    fun filterSweepsBothWays() {
        val high = TestSignals.sine(6000.0, 1.0, rate, 0.4f)
        val low = TestSignals.sine(80.0, 1.0, rate, 0.4f)
        assertTrue(rms(run(DjSettings(filter = -1f), high)) < rms(high) * 0.05, "lowpass keeps highs")
        assertTrue(rms(run(DjSettings(filter = 1f), low)) < rms(low) * 0.05, "highpass keeps lows")
        assertTrue(rms(run(DjSettings(filter = -1f), low)) > rms(low) * 0.7, "lowpass kills lows")
    }

    @Test
    fun echoRepeatsAfterSoundStops() {
        val burst = FloatArray(rate * 2).also { TestSignals.sine(440.0, 0.1, rate, 0.4f).copyInto(it) }
        val dry = run(DjSettings(), burst)
        val wet = run(DjSettings(echo = 1f), burst)
        val afterHalfBeat = rate / 4 + rate / 20
        assertTrue(rms(dry, afterHalfBeat, afterHalfBeat + rate / 10) < 1e-6)
        assertTrue(rms(wet, afterHalfBeat, afterHalfBeat + rate / 10) > 0.05, "no echo repeat")
    }

    @Test
    fun flangerAndCrushChangeSound() {
        val input = TestSignals.sine(440.0, 1.0, rate, 0.4f)
        for (settings in listOf(DjSettings(flanger = 1f), DjSettings(crush = 1f))) {
            val output = run(settings, input)
            val difference = (input.indices).sumOf { ((output[it] - input[it]) * (output[it] - input[it])).toDouble() }
            assertTrue(difference > 10.0, "$settings difference $difference")
        }
    }
}
