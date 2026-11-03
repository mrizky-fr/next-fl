package id.nextfl.core.dsp

import id.nextfl.core.pcm.PcmSource
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Onset-envelope autocorrelation tempo estimate. */
object BpmDetector {
    private const val ENVELOPE_RATE = 200
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0
    private const val BPM_STEP = 0.05
    private const val ANALYSIS_SECONDS = 60.0
    private const val MAX_SKIP_SECONDS = 30.0
    private const val MIN_ANALYSIS_SECONDS = 4.0
    private const val LOWPASS_HZ = 180.0
    private const val HIGH_BAND_WEIGHT = 0.6f
    private const val MEAN_WINDOW_SECONDS = 0.5
    private const val PRIOR_CENTER_BPM = 125.0
    private const val PRIOR_OCTAVE_WIDTH = 0.75
    private const val INTEGER_SNAP = 0.3
    private val HARMONIC_WEIGHTS = doubleArrayOf(1.0, 0.75, 0.5, 0.5)

    fun detect(source: PcmSource): Double? = detect(analysisSegment(source), source.sampleRate)

    fun detect(mono: FloatArray, sampleRate: Int): Double? {
        if (mono.size < sampleRate * MIN_ANALYSIS_SECONDS) return null
        val hop = (sampleRate.toDouble() / ENVELOPE_RATE).roundToInt()
        // Hop rounding shifts real rate
        val envelopeRate = sampleRate.toDouble() / hop
        val envelope = onsetEnvelope(mono, sampleRate, hop)
        val maxLag = ((60.0 * envelopeRate / MIN_BPM) * HARMONIC_WEIGHTS.size).toInt()
        val correlation = autocorrelate(envelope, min(maxLag, envelope.size - 1))
        return bestTempo(correlation, envelopeRate)?.let(::snap)
    }

    private fun analysisSegment(source: PcmSource): FloatArray {
        val duration = source.durationSeconds
        val skip = min(MAX_SKIP_SECONDS, max(0.0, (duration - ANALYSIS_SECONDS) / 2))
        val start = (skip * source.sampleRate).toInt()
        val length = min(source.frames - start, (ANALYSIS_SECONDS * source.sampleRate).toInt())
        return FloatArray(max(0, length)) { (source.left(start + it) + source.right(start + it)) * 0.5f }
    }

    private fun onsetEnvelope(mono: FloatArray, sampleRate: Int, hop: Int): FloatArray {
        val alpha = (1 - exp(-2 * Math.PI * LOWPASS_HZ / sampleRate)).toFloat()
        val envelope = FloatArray(mono.size / hop)
        var lowpass = 0f
        var previousLow = 0.0
        var previousHigh = 0.0
        for (frame in envelope.indices) {
            var lowEnergy = 0.0
            var highEnergy = 0.0
            for (i in frame * hop until frame * hop + hop) {
                lowpass += alpha * (mono[i] - lowpass)
                val highpass = mono[i] - lowpass
                lowEnergy += lowpass * lowpass
                highEnergy += highpass * highpass
            }
            val low = ln1p(1000 * lowEnergy / hop)
            val high = ln1p(1000 * highEnergy / hop)
            envelope[frame] = (max(0.0, low - previousLow) + HIGH_BAND_WEIGHT * max(0.0, high - previousHigh)).toFloat()
            previousLow = low
            previousHigh = high
        }
        return removeLocalMean(envelope, (ENVELOPE_RATE * MEAN_WINDOW_SECONDS).toInt())
    }

    private fun removeLocalMean(values: FloatArray, window: Int): FloatArray {
        val half = window / 2
        var sum = 0.0
        var count = 0
        for (i in 0 until min(half, values.size)) {
            sum += values[i]
            count++
        }
        return FloatArray(values.size) { i ->
            val enter = i + half
            val leave = i - half - 1
            if (enter < values.size) {
                sum += values[enter]
                count++
            }
            if (leave >= 0) {
                sum -= values[leave]
                count--
            }
            max(0.0, values[i] - sum / count).toFloat()
        }
    }

    private fun autocorrelate(values: FloatArray, maxLag: Int): DoubleArray {
        val result = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until values.size - lag) sum += values[i] * values[i + lag]
            result[lag] = sum / (values.size - lag)
        }
        return result
    }

    private fun bestTempo(correlation: DoubleArray, envelopeRate: Double): Double? {
        var bestBpm: Double? = null
        var bestScore = 0.0
        var bpm = MIN_BPM
        while (bpm <= MAX_BPM) {
            val score = harmonicScore(correlation, 60.0 * envelopeRate / bpm) * prior(bpm)
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += BPM_STEP
        }
        return bestBpm
    }

    private fun harmonicScore(correlation: DoubleArray, lag: Double): Double =
        HARMONIC_WEIGHTS.withIndex().sumOf { (index, weight) -> weight * sampleAt(correlation, lag * (index + 1)) }

    private fun sampleAt(values: DoubleArray, position: Double): Double {
        val index = floor(position).toInt()
        if (index + 1 >= values.size) return 0.0
        val fraction = position - index
        return values[index] * (1 - fraction) + values[index + 1] * fraction
    }

    private fun prior(bpm: Double): Double {
        val octaves = ln(bpm / PRIOR_CENTER_BPM) / ln(2.0) / PRIOR_OCTAVE_WIDTH
        return exp(-0.5 * octaves * octaves)
    }

    private fun snap(bpm: Double): Double {
        val nearest = Math.round(bpm).toDouble()
        return if (abs(bpm - nearest) < INTEGER_SNAP) nearest else Math.round(bpm * 10) / 10.0
    }
}
