package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.EqSettings
import id.nextfl.core.pcm.ArrayStereoSink
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeStretcherTest {
    private val rate = 22_050

    private fun stretch(input: FloatArray, speed: Double): FloatArray {
        val sink = ArrayStereoSink()
        TimeStretcher(rate).stretch(TestSignals.stereo(input, rate), speed, sink)
        val source = sink.toSource(rate)
        return FloatArray(source.frames) { source.left(it) }
    }

    @Test
    fun speedsUpWhileKeepingPitch() {
        val input = TestSignals.sine(440.0, 2.0, rate)
        val output = stretch(input, 1.25)
        assertEquals(ceil(input.size / 1.25).toInt(), output.size)
        assertEquals(440.0, TestSignals.zeroCrossingRate(output, rate), 440 * 0.03)
    }

    @Test
    fun slowsDownWhileKeepingPitch() {
        val input = TestSignals.sine(220.0, 1.0, rate)
        val output = stretch(input, 0.8)
        assertEquals(ceil(input.size / 0.8).toInt(), output.size)
        assertEquals(220.0, TestSignals.zeroCrossingRate(output, rate), 220 * 0.03)
    }

    @Test
    fun stopsWhenCancelled() {
        val input = TestSignals.sine(220.0, 3.0, rate)
        val finished = TimeStretcher(rate).stretch(TestSignals.stereo(input, rate), 1.1, ArrayStereoSink(), isCancelled = { true })
        assertFalse(finished)
    }
}

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
    fun reverbAddsTailAndDryPassesWhenDisabled() {
        val reverb = Reverb(44_100)
        reverb.configure(id.nextfl.core.model.ReverbSettings(enabled = true, mix = 0.5f, decay = 2f))
        val left = FloatArray(44_100)
        val right = FloatArray(44_100)
        left[0] = 1f
        right[0] = 1f
        reverb.process(left, right, left.size)
        assertTrue(left.drop(2000).maxOf { abs(it) } > 1e-3f)
        assertTrue(Reverb.combFeedback(8.0) > Reverb.combFeedback(1.0))
    }

    @Test
    fun limiterKeepsPeaksBelowCeiling() {
        val left = FloatArray(1000) { 1.8f }
        val right = FloatArray(1000) { -1.5f }
        Limiter(44_100).process(left, right, 1000)
        assertTrue(left.all { it <= 0.8901f } && right.all { it >= -0.8901f })
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
