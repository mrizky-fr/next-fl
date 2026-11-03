package id.nextfl.core.pcm

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class WaveformPeaks(val blockSize: Int, val sampleRate: Int, val min: FloatArray, val max: FloatArray) {
    val blocksPerSecond: Double get() = sampleRate.toDouble() / blockSize

    fun peakOfRange(startSeconds: Double, endSeconds: Double): Float {
        val first = max(0, floor(startSeconds * blocksPerSecond).toInt())
        val last = min(max.size, ceil(endSeconds * blocksPerSecond).toInt())
        var peak = 0f
        for (i in first until last) peak = max(peak, max(max[i], -min[i]))
        return peak
    }

    companion object {
        const val BLOCK_SIZE = 256

        fun compute(source: PcmSource): WaveformPeaks {
            val accumulator = PeakAccumulator(source.sampleRate)
            for (frame in 0 until source.frames) accumulator.add(source.left(frame), source.right(frame))
            return accumulator.build()
        }
    }
}

class PeakAccumulator(private val sampleRate: Int) : StereoSink {
    private var mins = FloatArray(1024)
    private var maxs = FloatArray(1024)
    private var blocks = 0
    private var inBlock = 0
    private var low = 0f
    private var high = 0f

    override fun write(left: FloatArray, right: FloatArray, count: Int) {
        for (i in 0 until count) add(left[i], right[i])
    }

    fun add(left: Float, right: Float) {
        low = min(low, min(left, right))
        high = max(high, max(left, right))
        if (++inBlock == WaveformPeaks.BLOCK_SIZE) closeBlock()
    }

    fun build(): WaveformPeaks {
        if (inBlock > 0) closeBlock()
        return WaveformPeaks(WaveformPeaks.BLOCK_SIZE, sampleRate, mins.copyOf(blocks), maxs.copyOf(blocks))
    }

    private fun closeBlock() {
        if (blocks == mins.size) {
            mins = mins.copyOf(blocks * 2)
            maxs = maxs.copyOf(blocks * 2)
        }
        mins[blocks] = low
        maxs[blocks] = high
        blocks++
        inBlock = 0
        low = 0f
        high = 0f
    }
}

/** Fans out to many sinks. */
class TeeSink(private vararg val sinks: StereoSink) : StereoSink {
    override fun write(left: FloatArray, right: FloatArray, count: Int) = sinks.forEach { it.write(left, right, count) }
}
