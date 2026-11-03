package id.nextfl.core.analysis

import kotlin.math.max
import kotlin.math.min

data class BeatResult(val beat: Boolean, val strength: Float)

/** Adaptive bass-energy beat detector. */
class BeatTracker {
    private val times = ArrayDeque<Double>()
    private val values = ArrayDeque<Double>()
    private var sum = 0.0
    private var time = 0.0
    private var lastBeat = Double.NEGATIVE_INFINITY

    fun update(energy: Double, dt: Double, sensitivity: Float): BeatResult {
        time += dt
        val average = if (values.isEmpty()) 0.0 else sum / values.size
        val threshold = BASE_THRESHOLD + SENSITIVITY_SPAN / max(0.1f, sensitivity)
        val isBeat = values.size >= MIN_HISTORY_FRAMES &&
            energy > MIN_ENERGY &&
            energy > average * threshold &&
            time - lastBeat > MIN_BEAT_INTERVAL

        push(energy)
        if (!isBeat) return NO_BEAT
        lastBeat = time
        val ratio = energy / max(average, MIN_ENERGY)
        val strength = min(1.0, max(MIN_STRENGTH, (ratio - threshold) / threshold + STRENGTH_BIAS))
        return BeatResult(true, strength.toFloat())
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
        const val MIN_BEAT_INTERVAL = 0.22
        const val BASE_THRESHOLD = 1.0
        const val SENSITIVITY_SPAN = 0.6
        const val MIN_ENERGY = 3e-5
        const val MIN_STRENGTH = 0.3
        const val STRENGTH_BIAS = 0.4
        val NO_BEAT = BeatResult(false, 0f)
    }
}
