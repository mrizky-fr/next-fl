package id.nextfl.core.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Instant-attack peak limiter. */
class Limiter(sampleRate: Int, private val ceiling: Float = 0.89f, releaseSeconds: Double = 0.12) {
    private val release = exp(-1.0 / (releaseSeconds * sampleRate)).toFloat()
    private var gain = 1f

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        for (i in 0 until count) {
            val peak = max(abs(left[i]), abs(right[i]))
            val target = if (peak > ceiling) ceiling / peak else 1f
            gain = if (target < gain) target else target + (gain - target) * release
            left[i] = (left[i] * gain).coerceIn(-1f, 1f)
            right[i] = (right[i] * gain).coerceIn(-1f, 1f)
        }
    }
}
