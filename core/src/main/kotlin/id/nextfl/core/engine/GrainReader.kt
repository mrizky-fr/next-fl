package id.nextfl.core.engine

import id.nextfl.core.pcm.PcmSource
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Realtime WSOLA: tempo and pitch apart. */
internal class GrainReader(sampleRate: Int) {
    private val grain = (sampleRate * GRAIN_SECONDS).roundToInt() / 2 * 2
    private val hop = grain / 2
    private val seek = (sampleRate * SEEK_SECONDS).roundToInt()
    private val window = FloatArray(grain) { (0.5 - 0.5 * cos(2 * PI * it / grain)).toFloat() }
    private val taps = hop / STRIDE
    private val reference = FloatArray(taps)
    private val offsets = IntArray(taps)
    private var region = FloatArray(2 * seek + taps * STRIDE * PRESIZED_STEP + 2)

    var left = 0f
        private set
    var right = 0f
        private set

    /** Aligned grain starts per voice. */
    class State {
        internal var index = Long.MIN_VALUE
        internal var current = 0.0
        internal var previous = 0.0
        internal var currentStep = 1.0
        internal var previousStep = 1.0
    }

    // Two Hann grains, half overlap
    fun sample(voice: ClipVoice, state: State, local: Long) {
        val k = Math.floorDiv(local, hop.toLong())
        if (k != state.index) advance(voice, state, k)
        val u = (local - k * hop).toInt()
        val a = state.current + u * state.currentStep
        val b = state.previous + (u + hop) * state.previousStep
        val wa = window[u]
        val wb = window[u + hop]
        left = wa * Interpolator.left(voice.source, a) + wb * Interpolator.left(voice.source, b)
        right = wa * Interpolator.right(voice.source, a) + wb * Interpolator.right(voice.source, b)
    }

    // Step frozen per grain: no clicks
    private fun advance(voice: ClipVoice, state: State, k: Long) {
        val step = voice.grainStep
        if (k == state.index + 1) {
            state.previous = state.current
            state.previousStep = state.currentStep
        } else {
            state.previous = ideal(voice, k - 1)
            state.previousStep = step
        }
        state.currentStep = step
        state.index = k
        if (k <= 0L) {
            startAtClipOffset(voice, state)
            return
        }
        val continuation = state.previous + hop * state.previousStep
        state.current = align(voice, ideal(voice, k), continuation, state.previousStep)
    }

    // Twin grains: exact source, no fade
    private fun startAtClipOffset(voice: ClipVoice, state: State) {
        state.current = voice.sourceStart
        state.previous = voice.sourceStart - hop * state.currentStep
        state.previousStep = state.currentStep
    }

    private fun ideal(voice: ClipVoice, k: Long) = voice.sourceStart + k * hop * voice.step

    // Best match to previous continuation
    private fun align(voice: ClipVoice, ideal: Double, continuation: Double, previousStep: Double): Double {
        val source = voice.source
        val step = voice.grainStep
        for (j in 0 until taps) {
            offsets[j] = (j * STRIDE * step).toInt()
            reference[j] = mono(source, (continuation + j * STRIDE * previousStep).toInt())
        }
        val base = floor(ideal).toInt() - seek
        val span = 2 * seek + ceil(taps * STRIDE * step).toInt() + 2
        if (region.size < span) region = FloatArray(span)
        for (j in 0 until span) region[j] = mono(source, base + j)
        val range = 2 * seek
        val earliest = (ceil(voice.sourceStart).toInt() - base).coerceIn(0, range)
        var best = max(seek, earliest)
        var bestScore = Double.NEGATIVE_INFINITY
        var offset = earliest
        while (offset <= range) {
            val score = similarity(offset)
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
            offset += COARSE
        }
        val coarse = best
        for (fine in max(earliest, coarse - COARSE + 1)..min(range, coarse + COARSE - 1)) {
            val score = similarity(fine)
            if (score > bestScore) {
                bestScore = score
                best = fine
            }
        }
        return base + best + (ideal - floor(ideal))
    }

    private fun similarity(offset: Int): Double {
        var dot = 0.0
        var energy = 1e-9
        for (j in 0 until taps) {
            val value = region[offset + offsets[j]]
            dot += reference[j] * value
            energy += value * value
        }
        return dot / sqrt(energy)
    }

    private fun mono(source: PcmSource, frame: Int) = (source.left(frame) + source.right(frame)) * 0.5f

    private companion object {
        const val GRAIN_SECONDS = 0.06
        const val SEEK_SECONDS = 0.012
        const val STRIDE = 8
        const val COARSE = 4
        const val PRESIZED_STEP = 4
    }
}
