package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.DjSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class DjEffectsSmoothTest {
    private val rate = 44_100
    private val block = 512

    private fun run(input: FloatArray, settingsAt: (Int) -> DjSettings, bpmAt: (Int) -> Double = { 120.0 }): FloatArray {
        val fx = DjEffects(rate)
        val output = input.copyOf()
        var offset = 0
        var index = 0
        while (offset < output.size) {
            val count = minOf(block, output.size - offset)
            val left = output.copyOfRange(offset, offset + count)
            val right = left.copyOf()
            fx.process(settingsAt(index), bpmAt(index), left, right, count)
            left.copyInto(output, offset)
            offset += count
            index++
        }
        return output
    }

    private fun maxJump(data: FloatArray): Float {
        var jump = 0f
        for (i in 1 until data.size) jump = maxOf(jump, abs(data[i] - data[i - 1]))
        return jump
    }

    // Jog wheel moves BPM per block
    @Test
    fun echoGlidesWhenBpmMoves() {
        val input = TestSignals.sine(220.0, 1.5, rate, 0.3f)
        val output = run(input, { DjSettings(echo = 1f) }, { 120.0 + it })
        assertTrue(maxJump(output) < 0.15f, "echo click ${maxJump(output)}")
    }

    @Test
    fun flangerSwitchesOnWithoutClick() {
        val input = TestSignals.sine(200.0, 1.0, rate, 0.4f)
        val output = run(input, { if (it < 4) DjSettings() else DjSettings(flanger = 1f) })
        assertTrue(maxJump(output) < 0.05f, "flanger click ${maxJump(output)}")
    }

    @Test
    fun crushSurvivesNaNInput() {
        val input = FloatArray(block * 2) { if (it == 3) Float.NaN else 0.2f }
        run(input, { DjSettings(crush = 1f) })
    }

    @Test
    fun filterKnobJumpDoesNotClick() {
        val input = TestSignals.sine(100.0, 1.0, rate, 0.4f)
        val output = run(input, { if (it % 8 < 4) DjSettings() else DjSettings(filter = -0.9f) })
        assertTrue(maxJump(output) < 0.05f, "filter zipper ${maxJump(output)}")
    }
}
