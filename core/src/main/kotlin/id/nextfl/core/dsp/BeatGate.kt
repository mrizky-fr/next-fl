package id.nextfl.core.dsp

import kotlin.math.exp

/** Beat-locked chopper, open first half. */
class BeatGate(private val sampleRate: Int) {
    private val glide = (1 - exp(-1.0 / (EDGE_SECONDS * sampleRate))).toFloat()
    private var gain = 1f
    private var activeDivision = OFF
    private var phase = 0.0
    private var nextFrame = Long.MIN_VALUE

    // Division in beats; OFF bypasses
    fun process(division: Double, bpm: Double, startFrame: Long, left: FloatArray, right: FloatArray, frames: Int) {
        val on = division > 0 && bpm > 0
        if (on && (division != activeDivision || startFrame != nextFrame)) phase = 0.0
        activeDivision = if (on) division else OFF
        nextFrame = startFrame + frames
        if (!on && gain >= OPEN_FLOOR) {
            gain = 1f
            return
        }
        val period = if (on) sampleRate * SECONDS_PER_MINUTE / bpm * division else 1.0
        for (i in 0 until frames) {
            val open = !on || phase < DUTY
            gain += ((if (open) 1f else 0f) - gain) * glide
            left[i] *= gain
            right[i] *= gain
            if (on) {
                phase += 1.0 / period
                if (phase >= 1.0) phase -= 1.0
            }
        }
    }

    companion object {
        const val OFF = 0.0
        private const val DUTY = 0.5
        private const val EDGE_SECONDS = 0.001
        private const val OPEN_FLOOR = 0.999f
        private const val SECONDS_PER_MINUTE = 60.0
    }
}
