package id.nextfl.core.analysis

import kotlin.math.max
import kotlin.math.min

data class BeatResult(val beat: Boolean, val strength: Float)

/** Onset picker: rise from valley. */
class BeatTracker {
    private val times = ArrayDeque<Double>()
    private val values = ArrayDeque<Double>()
    private var sum = 0.0
    private var time = 0.0
    private var lastBeat = Double.NEGATIVE_INFINITY
    private var armed = true
    private var valley = Double.MAX_VALUE
    private var peak = 0.0

    fun update(energy: Double, dt: Double, sensitivity: Float): BeatResult {
        time += dt
        val average = if (values.isEmpty()) 0.0 else sum / values.size
        val gentle = max(MIN_SENSITIVITY, sensitivity)
        val loud = LOUD_BASE + LOUD_SPAN / gentle
        val rise = 1.0 + RISE_SPAN / gentle
        val isBeat = armed &&
            values.size >= MIN_HISTORY_FRAMES &&
            energy > MIN_ENERGY &&
            energy > average * loud &&
            energy > valley * rise &&
            time - lastBeat > MIN_BEAT_INTERVAL
        push(energy)
        if (!isBeat) {
            track(energy)
            return NO_BEAT
        }
        lastBeat = time
        armed = false
        peak = energy
        val ratio = energy / max(average, MIN_ENERGY)
        val strength = min(1.0, max(MIN_STRENGTH, (ratio - loud) / loud + STRENGTH_BIAS))
        return BeatResult(true, strength.toFloat())
    }

    // Re-arms once level dips
    private fun track(energy: Double) {
        if (armed) {
            valley = min(valley, energy)
            return
        }
        peak = max(peak, energy)
        if (energy < peak * REARM_DIP) {
            armed = true
            valley = energy
        }
    }

    private fun push(energy: Double) {
        times.addLast(time)
        values.addLast(energy)
        sum += energy
        while (times.isNotEmpty() && time - times.first() > HISTORY_SECONDS) {
            times.removeFirst()
            sum -= values.removeFirst()
        }
    }

    private companion object {
        const val HISTORY_SECONDS = 0.9
        const val MIN_HISTORY_FRAMES = 8
        const val MIN_BEAT_INTERVAL = 0.09
        const val MIN_SENSITIVITY = 0.1f
        const val LOUD_BASE = 0.6
        const val LOUD_SPAN = 0.3
        const val RISE_SPAN = 0.5
        const val REARM_DIP = 0.7
        const val MIN_ENERGY = 3e-5
        const val MIN_STRENGTH = 0.45
        const val STRENGTH_BIAS = 0.4
        val NO_BEAT = BeatResult(false, 0f)
    }
}
