package id.nextfl.core.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Metronome swing locked to beats, -1..1. */
class BeatSwing {
    var value = 0f
        private set
    private var from = 0f
    private var to = 0f
    private var side = 1f
    private var sinceBeat = NEVER
    private var interval = DEFAULT_INTERVAL
    private val gaps = FloatArray(HISTORY)
    private val sorted = FloatArray(HISTORY)
    private var gapCount = 0
    private var gapNext = 0

    // Fast on the beat, eases into extreme
    fun update(beat: Boolean, dt: Float) {
        sinceBeat += dt
        if (beat && sinceBeat >= interval * MIN_PHASE) swing()
        if (sinceBeat > interval * IDLE_BEATS) {
            value *= exp(-dt / SETTLE_SECONDS)
            if (abs(value) < SNAP) value = 0f
            return
        }
        val phase = (sinceBeat / interval).coerceIn(0f, 1f)
        value = from + (to - from) * (1f - (1f - phase).pow(EASE_POWER))
    }

    private fun swing() {
        learn(sinceBeat)
        from = value
        to = side
        side = -side
        sinceBeat = 0f
    }

    // Median ignores missed beats
    private fun learn(gap: Float) {
        if (gap !in MIN_INTERVAL..MAX_INTERVAL) return
        gaps[gapNext] = gap
        gapNext = (gapNext + 1) % HISTORY
        if (gapCount < HISTORY) gapCount++
        gaps.copyInto(sorted, 0, 0, gapCount)
        sorted.sort(0, gapCount)
        interval = sorted[gapCount / 2]
    }

    private companion object {
        const val NEVER = 1e6f
        const val DEFAULT_INTERVAL = 0.5f
        const val MIN_INTERVAL = 0.25f
        const val MAX_INTERVAL = 1.5f
        const val HISTORY = 5

        // Earlier beats are off-beats
        const val MIN_PHASE = 0.55f
        const val IDLE_BEATS = 3f
        const val SETTLE_SECONDS = 0.35f
        const val SNAP = 1e-4f
        const val EASE_POWER = 3f
    }
}
