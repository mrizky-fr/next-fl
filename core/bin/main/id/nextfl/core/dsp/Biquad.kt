package id.nextfl.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { LOW_PASS, HIGH_PASS, LOW_SHELF, HIGH_SHELF, PEAKING }

/** RBJ cookbook biquad coefficients. */
data class BiquadCoefficients(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    fun magnitudeDb(frequency: Double, sampleRate: Int): Double {
        val w = 2 * PI * frequency / sampleRate
        val cos1 = cos(w)
        val cos2 = cos(2 * w)
        val sin1 = sin(w)
        val sin2 = sin(2 * w)
        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)
        val magnitude = sqrt((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
        return 20 * log10(magnitude.coerceAtLeast(1e-9))
    }

    companion object {
        private const val SHELF_SLOPE_ALPHA_FACTOR = 1.4142135623730951

        fun design(type: FilterType, frequency: Double, sampleRate: Int, q: Double = 0.7071, gainDb: Double = 0.0): BiquadCoefficients {
            val w0 = 2 * PI * frequency.coerceIn(10.0, sampleRate * 0.45) / sampleRate
            val cosW = cos(w0)
            val sinW = sin(w0)
            val a = 10.0.pow(gainDb / 40)
            return when (type) {
                FilterType.LOW_PASS -> pass(cosW, sinW / (2 * q), low = true)
                FilterType.HIGH_PASS -> pass(cosW, sinW / (2 * q), low = false)
                FilterType.PEAKING -> peaking(cosW, sinW / (2 * q), a)
                FilterType.LOW_SHELF -> shelf(cosW, sinW / 2 * SHELF_SLOPE_ALPHA_FACTOR, a, low = true)
                FilterType.HIGH_SHELF -> shelf(cosW, sinW / 2 * SHELF_SLOPE_ALPHA_FACTOR, a, low = false)
            }
        }

        private fun pass(cosW: Double, alpha: Double, low: Boolean): BiquadCoefficients {
            val b1 = if (low) 1 - cosW else -(1 + cosW)
            val b0 = if (low) (1 - cosW) / 2 else (1 + cosW) / 2
            return normalize(b0, b1, b0, 1 + alpha, -2 * cosW, 1 - alpha)
        }

        private fun peaking(cosW: Double, alpha: Double, a: Double) =
            normalize(1 + alpha * a, -2 * cosW, 1 - alpha * a, 1 + alpha / a, -2 * cosW, 1 - alpha / a)

        private fun shelf(cosW: Double, alpha: Double, a: Double, low: Boolean): BiquadCoefficients {
            val root = 2 * sqrt(a) * alpha
            val sign = if (low) 1 else -1
            val b0 = a * ((a + 1) - sign * (a - 1) * cosW + root)
            val b1 = sign * 2 * a * ((a - 1) - sign * (a + 1) * cosW)
            val b2 = a * ((a + 1) - sign * (a - 1) * cosW - root)
            val a0 = (a + 1) + sign * (a - 1) * cosW + root
            val a1 = -sign * 2 * ((a - 1) + sign * (a + 1) * cosW)
            val a2 = (a + 1) + sign * (a - 1) * cosW - root
            return normalize(b0, b1, b2, a0, a1, a2)
        }

        private fun normalize(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}

/** Stereo transposed direct form. */
class StereoBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1L = 0f
    private var z2L = 0f
    private var z1R = 0f
    private var z2R = 0f

    fun set(coefficients: BiquadCoefficients) {
        b0 = coefficients.b0.toFloat()
        b1 = coefficients.b1.toFloat()
        b2 = coefficients.b2.toFloat()
        a1 = coefficients.a1.toFloat()
        a2 = coefficients.a2.toFloat()
    }

    fun reset() {
        z1L = 0f; z2L = 0f; z1R = 0f; z2R = 0f
    }

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        for (i in 0 until count) {
            val xl = left[i]
            val yl = b0 * xl + z1L
            z1L = b1 * xl - a1 * yl + z2L
            z2L = b2 * xl - a2 * yl
            left[i] = yl
            val xr = right[i]
            val yr = b0 * xr + z1R
            z1R = b1 * xr - a1 * yr + z2R
            z2R = b2 * xr - a2 * yr
            right[i] = yr
        }
        if (kotlin.math.abs(z1L) < DENORMAL) z1L = 0f
        if (kotlin.math.abs(z1R) < DENORMAL) z1R = 0f
    }

    private companion object {
        const val DENORMAL = 1e-20f
    }
}
