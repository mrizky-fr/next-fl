package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.EqSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BpmDetectorTest {
    private val rate = 22_050

    @Test
    fun detectsCommonTempos() {
        for (bpm in listOf(128.0, 92.0, 140.0, 174.0)) {
            val detected = BpmDetector.detect(TestSignals.drumLoop(bpm, 40.0, rate), rate)
            assertNotNull(detected, "bpm $bpm")
            assertTrue(abs(detected - bpm) <= 0.5, "expected $bpm got $detected")
        }
    }

    @Test
    fun exactIntegerTempoAtCdRate() {
        val rate = 44_100
        assertEquals(128.0, BpmDetector.detect(TestSignals.drumLoop(128.0, 40.0, rate), rate))
    }

    @Test
    fun ignoresShortAudio() {
        assertNull(BpmDetector.detect(TestSignals.sine(100.0, 1.0, rate), rate))
    }
}

class FilterTest {
    @Test
    fun peakingBoostsCenterFrequency() {
        val design = BiquadCoefficients.design(FilterType.PEAKING, 1000.0, 44_100, 0.9, 6.0)
        assertEquals(6.0, design.magnitudeDb(1000.0, 44_100), 0.05)
        assertEquals(0.0, design.magnitudeDb(20.0, 44_100), 0.2)
    }

    @Test
    fun highPassAttenuatesBelowCutoff() {
        val design = BiquadCoefficients.design(FilterType.HIGH_PASS, 500.0, 44_100)
        assertEquals(-3.0, design.magnitudeDb(500.0, 44_100), 0.1)
        assertTrue(design.magnitudeDb(50.0, 44_100) < -35)
    }

    @Test
    fun neutralEqIsTransparent() {
        assertEquals(0.0, Equalizer.responseDb(EqSettings(), 1000.0, 44_100), 1e-9)
        val boosted = EqSettings(lowGain = 6f)
        assertTrue(Equalizer.responseDb(boosted, 40.0, 44_100) > 5)
    }

    @Test
    fun equalizerProcessesSignal() {
        val left = TestSignals.sine(60.0, 0.5, 44_100)
        val right = left.copyOf()
        val eq = Equalizer(44_100)
        eq.configure(EqSettings(lowCut = 400f))
        eq.process(left, right, left.size)
        val tail = left.takeLast(4000).maxOf { abs(it) }
        assertTrue(tail < 0.05f, "60 Hz should be cut, got $tail")
    }

    @Test
    fun limiterKeepsPeaksBelowCeiling() {
        val left = FloatArray(1000) { 1.8f }
        val right = FloatArray(1000) { -1.5f }
        Limiter(44_100).process(left, right, 1000)
        assertTrue(left.all { it <= 0.8901f } && right.all { it >= -0.8901f })
    }
}

class ReverbTest {
    private val rate = 44_100
    private val settings = id.nextfl.core.model.ReverbSettings(enabled = true, mix = 1f, decay = 2f, preDelay = 0f, tone = 18_000f)

    private fun impulseResponse(seconds: Double): Pair<FloatArray, FloatArray> {
        val frames = (seconds * rate).toInt()
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        left[0] = 1f
        right[0] = 1f
        Reverb(rate).apply { configure(settings) }.process(left, right, frames)
        return left to right
    }

    private fun energy(data: FloatArray, from: Double, to: Double): Double =
        (( from * rate).toInt() until (to * rate).toInt()).sumOf { (data[it] * data[it]).toDouble() }

    @Test
    fun tailDecaysAtConfiguredTime() {
        val (left, _) = impulseResponse(2.4)
        val early = energy(left, 0.1, 0.3)
        val late = energy(left, 1.9, 2.1)
        assertTrue(early > 0 && late > 0)
        assertTrue(early / late in 1e3..1e8, "ratio ${early / late}")
    }

    @Test
    fun tailIsDenseNotMetallic() {
        val (left, right) = impulseResponse(0.6)
        val window = left.copyOfRange((0.3 * rate).toInt(), (0.4 * rate).toInt())
        val rms = kotlin.math.sqrt(window.sumOf { (it * it).toDouble() } / window.size)
        val crest = window.maxOf { abs(it) } / rms
        assertTrue(crest < 5.5, "crest $crest")
        val tailR = right.copyOfRange((0.3 * rate).toInt(), (0.4 * rate).toInt())
        val correlation = window.indices.sumOf { (window[it] * tailR[it]).toDouble() } /
            kotlin.math.sqrt(window.sumOf { (it * it).toDouble() } * tailR.sumOf { (it * it).toDouble() })
        assertTrue(abs(correlation) < 0.5, "correlation $correlation")
    }

    @Test
    fun wetLevelStaysNearDryLevel() {
        val random = kotlin.random.Random(5)
        val frames = rate * 3
        val left = FloatArray(frames) { (random.nextFloat() * 2 - 1) * 0.3f }
        val right = left.copyOf()
        val dryRms = kotlin.math.sqrt(left.sumOf { (it * it).toDouble() } / frames)
        Reverb(rate).apply { configure(settings) }.process(left, right, frames)
        val wetRms = kotlin.math.sqrt((rate until frames).sumOf { (left[it] * left[it]).toDouble() } / (frames - rate))
        println("reverb wet/dry rms ratio = ${wetRms / dryRms}")
        assertTrue(wetRms / dryRms in 0.3..3.0, "ratio ${wetRms / dryRms}")
    }

    @Test
    fun feedbackMatchesRt60() {
        assertEquals(0.8414, Reverb.lineFeedback(0.05f, 2.0).toDouble(), 1e-3)
    }
}

class EqualizerSmoothingTest {
    @Test
    fun gainGlidesInsteadOfJumping() {
        val rate = 44_100
        val eq = Equalizer(rate)
        eq.configure(EqSettings())
        eq.configure(EqSettings(lowGain = 12f))
        val left = TestSignals.sine(60.0, 0.5, rate, 0.1f)
        val right = left.copyOf()
        eq.process(left, right, left.size)
        val early = left.copyOfRange(0, 256).maxOf { abs(it) }
        val late = left.copyOfRange(rate / 4, rate / 2).maxOf { abs(it) }
        assertTrue(early < late * 0.6f, "early $early late $late")
        assertTrue(left.all { it.isFinite() })
    }
}

class FftTest {
    @Test
    fun findsSinePeak() {
        val size = 1024
        val real = FloatArray(size) { kotlin.math.sin(2 * Math.PI * 64 * it / size).toFloat() }
        val imag = FloatArray(size)
        Fft(size).transform(real, imag)
        val magnitudes = FloatArray(size / 2) { kotlin.math.hypot(real[it], imag[it]) }
        assertEquals(64, magnitudes.indices.maxBy { magnitudes[it] })
    }
}
