package id.nextfl.core.pcm

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Windowed-sinc sample-rate converter, streaming. */
class StreamingResampler(inRate: Int, outRate: Int, private val sink: StereoSink) : StereoSink {
    private val step = inRate.toDouble() / outRate
    private val table = buildTable(min(1.0, outRate.toDouble() / inRate) * CUTOFF_MARGIN)
    private var bufferL = FloatArray(INITIAL_CAPACITY)
    private var bufferR = FloatArray(INITIAL_CAPACITY)
    private var bufferStart = -HALF.toLong()
    private var bufferCount = HALF
    private var position = 0.0
    private val outL = FloatArray(OUT_CHUNK)
    private val outR = FloatArray(OUT_CHUNK)
    private var outCount = 0

    override fun write(left: FloatArray, right: FloatArray, count: Int) {
        append(left, right, count)
        produce()
        compact()
    }

    fun finish() {
        val silence = FloatArray(HALF + 1)
        append(silence, silence, silence.size)
        produce()
        flushOutput()
    }

    private fun append(left: FloatArray, right: FloatArray, count: Int) {
        if (bufferCount + count > bufferL.size) {
            val capacity = maxOf(bufferCount + count, bufferL.size * 2)
            bufferL = bufferL.copyOf(capacity)
            bufferR = bufferR.copyOf(capacity)
        }
        left.copyInto(bufferL, bufferCount, 0, count)
        right.copyInto(bufferR, bufferCount, 0, count)
        bufferCount += count
    }

    private fun produce() {
        while (true) {
            val base = floor(position).toLong()
            val first = (base - HALF + 1 - bufferStart).toInt()
            if (base + HALF >= bufferStart + bufferCount) return
            val phase = ((position - base) * PHASES).roundToInt().coerceIn(0, PHASES)
            val row = phase * TAPS
            var sumL = 0f
            var sumR = 0f
            for (tap in 0 until TAPS) {
                val weight = table[row + tap]
                sumL += bufferL[first + tap] * weight
                sumR += bufferR[first + tap] * weight
            }
            emit(sumL, sumR)
            position += step
        }
    }

    private fun compact() {
        val keepFrom = (floor(position).toLong() - HALF + 1 - bufferStart).toInt().coerceIn(0, bufferCount)
        if (keepFrom == 0) return
        bufferL.copyInto(bufferL, 0, keepFrom, bufferCount)
        bufferR.copyInto(bufferR, 0, keepFrom, bufferCount)
        bufferCount -= keepFrom
        bufferStart += keepFrom
    }

    private fun emit(left: Float, right: Float) {
        outL[outCount] = left
        outR[outCount] = right
        if (++outCount == OUT_CHUNK) flushOutput()
    }

    private fun flushOutput() {
        if (outCount == 0) return
        sink.write(outL, outR, outCount)
        outCount = 0
    }

    private companion object {
        const val TAPS = 32
        const val HALF = TAPS / 2
        const val PHASES = 512
        const val CUTOFF_MARGIN = 0.96
        const val INITIAL_CAPACITY = 1 shl 15
        const val OUT_CHUNK = 2048

        // One kernel row per phase
        fun buildTable(cutoff: Double): FloatArray {
            val table = FloatArray((PHASES + 1) * TAPS)
            for (phase in 0..PHASES) {
                val fraction = phase.toDouble() / PHASES
                for (tap in 0 until TAPS) {
                    val distance = tap - HALF + 1 - fraction
                    table[phase * TAPS + tap] = (cutoff * sinc(cutoff * distance) * blackman(distance)).toFloat()
                }
            }
            return table
        }

        fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        fun blackman(distance: Double): Double {
            val t = (distance + HALF) / TAPS
            if (t <= 0.0 || t >= 1.0) return 0.0
            return 0.42 - 0.5 * cos(2 * PI * t) + 0.08 * cos(4 * PI * t)
        }
    }
}
