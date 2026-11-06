package id.nextfl.core.dsp

import id.nextfl.core.TestSignals
import id.nextfl.core.model.CompressorSettings
import id.nextfl.core.model.FxChain
import id.nextfl.core.model.FxRanges
import id.nextfl.core.model.Sanitize
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressorTest {
    private val rate = 44_100
    private val block = 512

    private fun processBlocks(compressor: Compressor, left: FloatArray, right: FloatArray) {
        var offset = 0
        val scratchL = FloatArray(block)
        val scratchR = FloatArray(block)
        while (offset < left.size) {
            val frames = minOf(block, left.size - offset)
            left.copyInto(scratchL, 0, offset, offset + frames)
            right.copyInto(scratchR, 0, offset, offset + frames)
            compressor.process(scratchL, scratchR, frames)
            scratchL.copyInto(left, offset, 0, frames)
            scratchR.copyInto(right, offset, 0, frames)
            offset += frames
        }
    }

    private fun maxStep(data: FloatArray): Float = (1 until data.size).maxOf { abs(data[it] - data[it - 1]) }

    @Test
    fun defaultSettingsKeepUnityBelowThreshold() {
        val input = TestSignals.sine(440.0, 1.0, rate, 0.5f)
        val left = input.copyOf()
        val right = input.copyOf()
        processBlocks(Compressor(rate).apply { configure(CompressorSettings(enabled = true)) }, left, right)
        val worst = input.indices.maxOf { abs(left[it] - input[it]) }
        assertTrue(worst < 1e-5f, "default compressor changed level by $worst")
    }

    @Test
    fun steadyLevelFollowsRatio() {
        val settings = CompressorSettings(enabled = true, thresholdDb = -20f, ratio = 4f, kneeDb = 0f, attackMs = 1f, releaseMs = 50f)
        val left = FloatArray(rate) { 0.5f }
        val right = left.copyOf()
        processBlocks(Compressor(rate).apply { configure(settings) }, left, right)
        val inputDb = -6.0206f
        val expected = 10f.pow((inputDb - Compressor.reductionDb(inputDb, -20f, 4f, 0f)) / 20f)
        assertEquals(expected, left.last(), expected * 0.01f)
        assertEquals(10.485f, Compressor.reductionDb(inputDb, -20f, 4f, 0f), 0.01f)
    }

    @Test
    fun softKneeIsContinuous() {
        val below = Compressor.reductionDb(-23.001f, -20f, 4f, 6f)
        val above = Compressor.reductionDb(-16.999f, -20f, 4f, 6f)
        assertEquals(0f, below, 1e-3f)
        assertEquals(0.75f * 3f, above, 1e-2f)
        assertEquals(0f, Compressor.reductionDb(-20f, -20f, 4f, 0f), 1e-6f)
    }

    @Test
    fun switchingIsClickFree() {
        val settings = CompressorSettings(enabled = true, thresholdDb = -30f, ratio = 10f, attackMs = 1f, makeupDb = 0f)
        val compressor = Compressor(rate).apply { configure(settings) }
        val input = TestSignals.sine(220.0, 1.0, rate, 0.8f)
        val left = input.copyOf()
        val right = input.copyOf()
        val half = input.size / 2
        val first = left.copyOfRange(0, half)
        val firstR = right.copyOfRange(0, half)
        processBlocks(compressor, first, firstR)
        compressor.configure(settings.copy(enabled = false))
        val second = left.copyOfRange(half, input.size)
        val secondR = right.copyOfRange(half, input.size)
        processBlocks(compressor, second, secondR)
        val joined = first + second
        val sineStep = maxStep(input)
        assertTrue(maxStep(joined) < sineStep * 2, "toggle step ${maxStep(joined)} vs sine $sineStep")
    }

    @Test
    fun extremeSettingsStayFinite() {
        val random = Random(7)
        val left = FloatArray(rate) { random.nextFloat() * 2 - 1 }
        val right = FloatArray(rate) { random.nextFloat() * 2 - 1 }
        val settings = CompressorSettings(
            enabled = true,
            thresholdDb = FxRanges.COMP_THRESHOLD.start,
            ratio = FxRanges.COMP_RATIO.endInclusive,
            attackMs = FxRanges.COMP_ATTACK_MS.start,
            releaseMs = FxRanges.COMP_RELEASE_MS.start,
            kneeDb = 0f,
            makeupDb = FxRanges.COMP_MAKEUP.endInclusive,
            autoMakeup = true,
        )
        val compressor = Compressor(rate).apply { configure(settings) }
        processBlocks(compressor, left, right)
        assertTrue(left.all { it.isFinite() } && right.all { it.isFinite() })
        assertTrue(compressor.gainReductionDb > 0f, "gain reduction not reported")
    }

    @Test
    fun sanitizeClampsCompressor() {
        val wild = CompressorSettings(thresholdDb = -200f, ratio = 0f, attackMs = 0f, releaseMs = 1e6f, kneeDb = 99f, makeupDb = 99f)
        val clean = Sanitize.fx(FxChain(compressor = wild)).compressor
        assertEquals(FxRanges.COMP_THRESHOLD.start, clean.thresholdDb)
        assertEquals(FxRanges.COMP_RATIO.start, clean.ratio)
        assertEquals(FxRanges.COMP_ATTACK_MS.start, clean.attackMs)
        assertEquals(FxRanges.COMP_RELEASE_MS.endInclusive, clean.releaseMs)
        assertEquals(FxRanges.COMP_KNEE.endInclusive, clean.kneeDb)
        assertEquals(FxRanges.COMP_MAKEUP.endInclusive, clean.makeupDb)
    }
}
