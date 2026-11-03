package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.FxChain
import id.nextfl.core.model.FxPresets
import id.nextfl.core.model.GraphicEqSettings
import id.nextfl.core.model.LevellerSettings
import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.model.Sanitize
import id.nextfl.core.model.StereoizerSettings
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StereoizerTest {
    private val rate = 44_100

    private fun noise(frames: Int, seed: Int) = Random(seed).let { r -> FloatArray(frames) { (r.nextFloat() * 2 - 1) * 0.3f } }

    @Test
    fun monoSumIsPreserved() {
        val left = noise(rate, 1)
        val right = noise(rate, 2)
        val mid = FloatArray(rate) { (left[it] + right[it]) * 0.5f }
        Stereoizer(rate).apply { configure(StereoizerSettings(enabled = true, strength = 1f, timbre = 0.7f, focus = 0.5f)) }.process(left, right, rate)
        val worst = (0 until rate).maxOf { abs((left[it] + right[it]) * 0.5f - mid[it]) }
        assertTrue(worst < 1e-4f, "mono sum drift $worst")
    }

    @Test
    fun strengthWidensMonoSource() {
        val left = noise(rate, 3)
        val right = left.copyOf()
        Stereoizer(rate).apply { configure(StereoizerSettings(enabled = true, strength = 1f, focus = 0f)) }.process(left, right, rate)
        val side = (rate / 2 until rate).sumOf { ((left[it] - right[it]) * (left[it] - right[it])).toDouble() }
        assertTrue(side > 1.0, "side energy $side")
    }

    @Test
    fun disabledIsUntouched() {
        val left = noise(1000, 4)
        val right = noise(1000, 5)
        val before = left.copyOf()
        Stereoizer(rate).apply { configure(StereoizerSettings(enabled = false, strength = 1f)) }.process(left, right, 1000)
        assertTrue(left.contentEquals(before))
    }
}

class GraphicEqualizerTest {
    @Test
    fun bandGainLandsAtItsFrequency() {
        val gains = List(GraphicEqSettings.BANDS) { if (it == 3) 6f else 0f }
        val settings = GraphicEqSettings(enabled = true, gains = gains)
        assertEquals(6.0, GraphicEqualizer.responseDb(settings, 750.0, 44_100), 0.5)
        assertEquals(0.0, GraphicEqualizer.responseDb(settings.copy(enabled = false), 750.0, 44_100), 1e-9)
    }

    @Test
    fun processingBoostsSignal() {
        val rate = 44_100
        // Below shelf corner gets full boost
        val left = TestSignals.sine(40.0, 1.0, rate, 0.1f)
        val right = left.copyOf()
        val eq = GraphicEqualizer(rate)
        eq.configure(GraphicEqSettings(enabled = true, gains = List(GraphicEqSettings.BANDS) { if (it == 0) 12f else 0f }))
        eq.process(left, right, left.size)
        val late = left.copyOfRange(rate / 2, rate).maxOf { abs(it) }
        assertTrue(late > 0.25f, "40 Hz boost peak $late")
    }
}

class LevellerTest {
    @Test
    fun gainAndPanApply() {
        val left = FloatArray(2048) { 0.5f }
        val right = FloatArray(2048) { 0.5f }
        val leveller = Leveller()
        val settings = LevellerSettings(enabled = true, gainDb = -6f, pan = 1f)
        leveller.process(settings, left, right, 1024)
        leveller.process(settings, left, right, 2048)
        assertEquals(0f, left.last(), 1e-6f)
        assertEquals(0.5f * 0.501f, right.last(), 1e-3f)
    }
}

class ReverbFeatureTest {
    private val rate = 44_100

    // Silence first so wet gain settles
    private fun earlyEnergy(early: Float): Double {
        val start = rate / 4
        val frames = rate / 2
        val left = FloatArray(frames).also { it[start] = 1f }
        val right = left.copyOf()
        val settings = ReverbSettings(enabled = true, mix = 1f, preDelay = 0f, earlyReflections = early)
        Reverb(rate).apply { configure(settings) }.process(left, right, frames)
        return (start + 1 until start + (0.02 * rate).toInt()).sumOf { (left[it] * left[it]).toDouble() }
    }

    @Test
    fun earlyReflectionsAddEnergy() {
        assertTrue(earlyEnergy(1f) > earlyEnergy(0f) * 2, "early reflections had no effect")
    }

    @Test
    fun widthZeroIsMono() {
        val frames = rate / 2
        val left = FloatArray(frames).also { it[0] = 1f }
        val right = FloatArray(frames)
        Reverb(rate).apply { configure(ReverbSettings(enabled = true, mix = 1f, width = 0f)) }.process(left, right, frames)
        val worst = (rate / 10 until frames).maxOf { abs(left[it] - right[it]) }
        assertTrue(worst < 1e-4f, "width 0 left/right differ $worst")
    }
}

class FxPresetsTest {
    @Test
    fun presetsStayInsideRanges() {
        val chains = FxPresets.reverb.map { FxChain(reverb = it.settings) } +
            FxPresets.graphicEq.map { FxChain(graphicEq = it.settings) } +
            FxPresets.stereoizer.map { FxChain(stereoizer = it.settings) } +
            FxPresets.leveller.map { FxChain(leveller = it.settings) } +
            FxPresets.eq.map { FxChain(eq = it.settings) }
        chains.forEach { assertEquals(it, Sanitize.fx(it)) }
    }

    @Test
    fun presetNameMatchesIgnoringSwitch() {
        val hall = FxPresets.reverb.first { it.name == "Hall" }.settings.copy(enabled = false)
        assertEquals("Hall", FxPresets.nameOf(FxPresets.reverb, hall) { it.copy(enabled = true) })
        assertEquals(FxPresets.CUSTOM, FxPresets.nameOf(FxPresets.reverb, hall.copy(mix = 0.31f)) { it.copy(enabled = true) })
    }
}
