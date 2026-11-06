package id.nextfl.core.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** Lookahead peak limiter, program release. */
class Limiter(sampleRate: Int, private val ceiling: Float = CEILING) {
    private val lookahead = max(1, (sampleRate * LOOKAHEAD_SECONDS).roundToInt())
    private val delayL = FloatArray(lookahead)
    private val delayR = FloatArray(lookahead)
    private val smoothing = FloatArray(lookahead) { 1f }
    private val minValues = FloatArray(lookahead + 1)
    private val minFrames = LongArray(lookahead + 1)
    private val holdFrames = (sampleRate * HOLD_SECONDS).roundToInt()
    private val fastRelease = retention(sampleRate, FAST_RELEASE_SECONDS)
    private val slowRelease = retention(sampleRate, SLOW_RELEASE_SECONDS)
    private val sustainStep = 1f - retention(sampleRate, SUSTAIN_SECONDS)
    private var sum = lookahead.toDouble()
    private var cursor = 0
    private var frame = 0L
    private var minFront = 0
    private var minSize = 0
    private var envelope = 1f
    private var holdLeft = 0
    private var sustain = 0f

    /** Output delay in frames. */
    val latency: Int get() = lookahead - 1

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        for (i in 0 until count) {
            val peak = max(abs(left[i]), abs(right[i]))
            val required = if (peak > ceiling) ceiling / peak else 1f
            val level = follow(windowMin(required))
            sum += level - smoothing[cursor]
            smoothing[cursor] = level
            delayL[cursor] = left[i]
            delayR[cursor] = right[i]
            cursor = if (cursor + 1 == lookahead) 0 else cursor + 1
            if (cursor == 0) sum = smoothing.sum().toDouble()
            val gain = (sum / lookahead).toFloat()
            left[i] = (delayL[cursor] * gain).coerceIn(-ceiling, ceiling)
            right[i] = (delayR[cursor] * gain).coerceIn(-ceiling, ceiling)
            frame++
        }
    }

    // Sliding minimum over lookahead
    private fun windowMin(value: Float): Float {
        val capacity = minValues.size
        while (minSize > 0 && minValues[(minFront + minSize - 1) % capacity] >= value) minSize--
        val slot = (minFront + minSize) % capacity
        minValues[slot] = value
        minFrames[slot] = frame
        minSize++
        while (minFrames[minFront] <= frame - lookahead) {
            minFront = (minFront + 1) % capacity
            minSize--
        }
        return minValues[minFront]
    }

    // Instant attack, hold, then release
    private fun follow(target: Float): Float {
        if (target <= envelope) {
            envelope = target
            holdLeft = holdFrames
        } else if (target < 1f && target <= envelope * REARM_RATIO) {
            holdLeft = holdFrames
        } else if (holdLeft > 0) {
            holdLeft--
        } else {
            val release = fastRelease + (slowRelease - fastRelease) * sustain
            envelope = target + (envelope - target) * release
        }
        val limiting = if (target < 1f) 1f else 0f
        sustain += (limiting - sustain) * sustainStep
        return envelope
    }

    companion object {
        const val CEILING = 0.89f
        private const val LOOKAHEAD_SECONDS = 0.0015
        private const val HOLD_SECONDS = 0.015
        private const val FAST_RELEASE_SECONDS = 0.05
        private const val SLOW_RELEASE_SECONDS = 0.5
        private const val SUSTAIN_SECONDS = 0.4
        private const val REARM_RATIO = 1.02f

        private fun retention(sampleRate: Int, seconds: Double) = exp(-1.0 / (seconds * sampleRate)).toFloat()
    }
}
