package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LimiterTest {
    private val rate = 44_100
    private val block = 512

    private fun limit(input: FloatArray, limiter: Limiter): FloatArray {
        val output = input.copyOf()
        var offset = 0
        while (offset < output.size) {
            val count = minOf(block, output.size - offset)
            val left = output.copyOfRange(offset, offset + count)
            val right = left.copyOf()
            limiter.process(left, right, count)
            left.copyInto(output, offset)
            offset += count
        }
        return output
    }

    @Test
    fun quietSignalPassesUntouched() {
        val input = TestSignals.sine(440.0, 0.5, rate, 0.5f)
        val limiter = Limiter(rate)
        val output = limit(input, limiter)
        val delay = limiter.latency
        for (i in delay until input.size) assertEquals(input[i - delay], output[i])
    }

    // Old limiter wobbled inside bass cycles
    @Test
    fun loudBassKeepsSteadyGainBelowCeiling() {
        val input = TestSignals.sine(50.0, 2.0, rate, 1.6f)
        val limiter = Limiter(rate)
        val output = limit(input, limiter)
        assertTrue(output.all { abs(it) <= Limiter.CEILING }, "output over ceiling")
        val delay = limiter.latency
        val gains = (rate until input.size).filter { abs(input[it - delay]) > 1.2f }.map { output[it] / input[it - delay] }
        val spread = gains.maxOf { it } - gains.minOf { it }
        assertTrue(spread < 0.01f, "gain wobble $spread")
    }

    @Test
    fun shortPeakRecoversQuickly() {
        val input = FloatArray(rate) { if (it in 1000 until 1100) 1.8f else 0.3f }
        val output = limit(input, Limiter(rate))
        assertTrue(output.all { abs(it) <= Limiter.CEILING }, "output over ceiling")
        assertEquals(0.3f, output[rate / 2], 0.003f)
    }
}
