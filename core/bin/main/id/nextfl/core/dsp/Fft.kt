package id.nextfl.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 complex FFT. */
class Fft(val size: Int) {
    private val cosTable = FloatArray(size / 2) { cos(2 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2 * PI * it / size).toFloat() }
    private val reversed = IntArray(size) { reverseBits(it) }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    fun transform(real: FloatArray, imag: FloatArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val stride = size / length
            for (start in 0 until size step length) {
                for (k in 0 until half) {
                    val wr = cosTable[k * stride]
                    val wi = -sinTable[k * stride]
                    val a = start + k
                    val b = a + half
                    val tr = real[b] * wr - imag[b] * wi
                    val ti = real[b] * wi + imag[b] * wr
                    real[b] = real[a] - tr
                    imag[b] = imag[a] - ti
                    real[a] += tr
                    imag[a] += ti
                }
            }
            length *= 2
        }
    }

    private fun reverseBits(value: Int): Int {
        var result = 0
        var input = value
        var bits = size
        while (bits > 1) {
            result = (result shl 1) or (input and 1)
            input = input shr 1
            bits = bits shr 1
        }
        return result
    }
}
