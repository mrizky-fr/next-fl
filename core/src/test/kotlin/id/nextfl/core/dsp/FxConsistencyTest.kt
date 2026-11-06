package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.GraphicEqSettings
import id.nextfl.core.model.LevellerSettings
import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.model.StereoizerSettings
import id.nextfl.core.util.AudioMath
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Snap, bypass and tail consistency. */
class FxConsistencyTest {
    private val rate = 44_100
    private val block = 512

    private fun noise(frames: Int, seed: Int) = Random(seed).let { r -> FloatArray(frames) { (r.nextFloat() * 2 - 1) * 0.3f } }

    private fun peak(data: FloatArray, from: Int, to: Int): Float = (from until to).maxOf { abs(data[it]) }

    private fun maxStep(data: FloatArray, from: Int, to: Int): Float = (from + 1 until to).maxOf { abs(data[it] - data[it - 1]) }

    private fun silentBlocks(seconds: Double, process: (FloatArray, FloatArray) -> Unit) {
        val left = FloatArray(block)
        val right = FloatArray(block)
        repeat((seconds * rate / block).toInt()) {
            left.fill(0f)
            right.fill(0f)
            process(left, right)
        }
    }

    @Test
    fun graphicEqStartsAtTargetGain() {
        val gains = List(GraphicEqSettings.BANDS) { if (it == 2) 12f else 0f }
        val left = TestSignals.sine(375.0, 1.0, rate, 0.1f)
        val right = left.copyOf()
        GraphicEqualizer(rate).apply { configure(GraphicEqSettings(enabled = true, gains = gains)) }.process(left, right, left.size)
        val early = peak(left, EARLY_FROM, EARLY_TO)
        val steady = peak(left, rate / 2, rate / 2 + EARLY_TO)
        assertTrue(early > steady * 0.9f, "graphic EQ ramped in, early $early steady $steady")
    }

    @Test
    fun levellerStartsAtTargetGain() {
        val left = FloatArray(block) { 0.25f }
        val right = left.copyOf()
        Leveller().process(LevellerSettings(enabled = true, gainDb = 6f), left, right, block)
        assertEquals(0.25f * AudioMath.dbToGain(6f), left[0], 1e-4f)
    }

    @Test
    fun reverbStartsAtTargetMix() {
        val left = FloatArray(block) { 0.5f }
        val right = left.copyOf()
        Reverb(rate).apply { configure(ReverbSettings(enabled = true, mix = 1f)) }.process(left, right, block)
        assertTrue(abs(left[0]) < 1e-3f, "dry leaked at start ${left[0]}")
    }

    @Test
    fun reverbDryTrimAppliesAtZeroMix() {
        val left = FloatArray(block) { 0.5f }
        val right = left.copyOf()
        Reverb(rate).apply { configure(ReverbSettings(enabled = true, mix = 0f, dryDb = -6f)) }.process(left, right, block)
        assertEquals(0.5f * AudioMath.dbToGain(-6f), left.last(), 1e-3f)
    }

    @Test
    fun reverbReEnableHasNoStaleTail() {
        val reverb = Reverb(rate)
        val on = ReverbSettings(enabled = true, mix = 0.5f, decay = 4f)
        reverb.configure(on)
        reverb.process(noise(rate / 2, 1), noise(rate / 2, 2), rate / 2)
        reverb.configure(on.copy(enabled = false))
        silentBlocks(SETTLE_SECONDS) { l, r -> reverb.process(l, r, block) }
        reverb.configure(on)
        val left = FloatArray(rate / 4)
        val right = FloatArray(rate / 4)
        reverb.process(left, right, left.size)
        val worst = maxOf(peak(left, 0, left.size), peak(right, 0, right.size))
        assertTrue(worst < 1e-6f, "stale reverb tail $worst")
    }

    @Test
    fun stereoizerReEnableHasNoStaleHistory() {
        val stereoizer = Stereoizer(rate)
        val on = StereoizerSettings(enabled = true, strength = 1f, lrDelay = 0.5f)
        stereoizer.configure(on)
        stereoizer.process(noise(rate / 2, 3), noise(rate / 2, 4), rate / 2)
        stereoizer.configure(on.copy(enabled = false))
        silentBlocks(SETTLE_SECONDS) { l, r -> stereoizer.process(l, r, block) }
        stereoizer.configure(on)
        val left = FloatArray(rate / 10)
        val right = FloatArray(rate / 10)
        stereoizer.process(left, right, left.size)
        val worst = maxOf(peak(left, 0, left.size), peak(right, 0, right.size))
        assertTrue(worst < 1e-6f, "stale stereoizer history $worst")
    }

    // Regression: pre-delay jumped, stale buffer
    @Test
    fun reverbPreDelayChangeIsClickFree() {
        val settings = ReverbSettings(enabled = true, mix = 1f, decay = 0.3f, preDelay = 0f, earlyReflections = 1f, lowCut = 20f)
        val reverb = Reverb(rate).apply { configure(settings) }
        val input = TestSignals.sine(LOW_TONE_HZ, 1.0, rate, 0.5f)
        val before = input.copyOfRange(0, CHANGE_AT)
        reverb.process(before, before.copyOf(), before.size)
        reverb.configure(settings.copy(preDelay = PRE_DELAY_STEP_SECONDS))
        val after = input.copyOfRange(CHANGE_AT, CHANGE_AT + WINDOW)
        reverb.process(after, after.copyOf(), after.size)
        val steady = maxStep(before, CHANGE_AT - WINDOW, CHANGE_AT)
        val transition = maxStep(before + after, CHANGE_AT - 1, CHANGE_AT + WINDOW)
        assertTrue(transition < steady * 3, "pre-delay click $transition vs steady $steady")
    }

    // Regression: z2 never flushed
    @Test
    fun biquadTailFlushesToExactZero() {
        val filter = StereoBiquad().apply { set(BiquadCoefficients.design(FilterType.HIGH_PASS, LOW_CUT_HZ, rate)) }
        val left = FloatArray(block).also { it[0] = 1f }
        val right = left.copyOf()
        repeat((FLUSH_CHECK_SECONDS * rate / block).toInt()) {
            filter.process(left, right, 0, block)
            left.fill(0f)
            right.fill(0f)
        }
        filter.process(left, right, 0, block)
        assertTrue(left.all { it == 0f } && right.all { it == 0f }, "tail not flushed ${peak(left, 0, block)}")
    }

    private companion object {
        const val EARLY_FROM = 256
        const val EARLY_TO = 1024
        const val SETTLE_SECONDS = 0.6
        const val FLUSH_CHECK_SECONDS = 0.6
        const val LOW_CUT_HZ = 25.0
        const val LOW_TONE_HZ = 100.0
        const val CHANGE_AT = 22_160
        const val WINDOW = 4410
        const val PRE_DELAY_STEP_SECONDS = 0.001f
    }
}
