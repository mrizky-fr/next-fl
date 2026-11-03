package id.nextfl.core.dsp

import id.nextfl.core.pcm.PcmSource
import id.nextfl.core.pcm.StereoSink
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** WSOLA tempo change, pitch kept. */
class TimeStretcher(sampleRate: Int) {
    private val sequence = ms(sampleRate, SEQUENCE_MS)
    private val overlap = ms(sampleRate, OVERLAP_MS)
    private val seek = ms(sampleRate, SEEK_MS)
    private val hop = sequence - overlap

    /** False when cancelled. */
    fun stretch(
        source: PcmSource,
        rate: Double,
        sink: StereoSink,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Boolean {
        val total = ceil(source.frames / rate).toLong()
        val writer = ChunkWriter(sink, total)
        val tailL = FloatArray(overlap)
        val tailR = FloatArray(overlap)
        val window = FloatArray(2 * seek + overlap)
        val reference = FloatArray(overlap)

        var previous = 0
        emitFrame(source, previous, tailL, tailR, writer, crossfade = false)
        var frame = 1L
        while (writer.produced < total) {
            if (isCancelled()) return false
            if (frame % PROGRESS_EVERY == 0L) onProgress(writer.produced.toFloat() / total)
            val ideal = (frame * hop * rate).roundToInt()
            val best = findBest(source, previous + hop, ideal, window, reference)
            emitFrame(source, best, tailL, tailR, writer, crossfade = true)
            previous = best
            frame++
        }
        writer.flush()
        onProgress(1f)
        return true
    }

    private fun emitFrame(source: PcmSource, start: Int, tailL: FloatArray, tailR: FloatArray, writer: ChunkWriter, crossfade: Boolean) {
        for (i in 0 until hop) {
            var left = source.left(start + i)
            var right = source.right(start + i)
            if (crossfade && i < overlap) {
                val weight = i.toFloat() / overlap
                left = tailL[i] * (1 - weight) + left * weight
                right = tailR[i] * (1 - weight) + right * weight
            }
            writer.put(left, right)
        }
        for (i in 0 until overlap) {
            tailL[i] = source.left(start + hop + i)
            tailR[i] = source.right(start + hop + i)
        }
    }

    private fun findBest(source: PcmSource, referenceStart: Int, ideal: Int, window: FloatArray, reference: FloatArray): Int {
        val low = max(0, ideal - seek)
        val high = min(source.frames - overlap - 1, ideal + seek)
        if (high < low) return max(0, min(ideal, source.frames - 1))
        for (i in 0 until overlap) reference[i] = mono(source, referenceStart + i)
        val span = high - low + overlap
        for (i in 0 until span) window[i] = mono(source, low + i)

        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        fun consider(offset: Int) {
            val score = similarity(reference, window, offset)
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
        }
        val range = high - low
        for (offset in 0..range step COARSE_STEP) consider(offset)
        val coarse = best
        for (offset in max(0, coarse - COARSE_STEP + 1)..min(range, coarse + COARSE_STEP - 1)) consider(offset)
        return low + best
    }

    private fun similarity(reference: FloatArray, window: FloatArray, offset: Int): Double {
        var dot = 0.0
        var energy = 1e-9
        var i = 0
        while (i < overlap) {
            val value = window[offset + i]
            dot += reference[i] * value
            energy += value * value
            i += CORRELATION_STEP
        }
        return dot / sqrt(energy)
    }

    private fun mono(source: PcmSource, frame: Int) = (source.left(frame) + source.right(frame)) * 0.5f

    private class ChunkWriter(private val sink: StereoSink, private val limit: Long) {
        private val left = FloatArray(CHUNK)
        private val right = FloatArray(CHUNK)
        private var count = 0
        private var written = 0L
        val produced: Long get() = written + count

        fun put(l: Float, r: Float) {
            if (produced >= limit) return
            left[count] = l
            right[count] = r
            if (++count == CHUNK) flush()
        }

        fun flush() {
            if (count == 0) return
            sink.write(left, right, count)
            written += count
            count = 0
        }
    }

    companion object {
        private const val SEQUENCE_MS = 80
        private const val SEEK_MS = 15
        private const val OVERLAP_MS = 10
        private const val COARSE_STEP = 4
        private const val CORRELATION_STEP = 4
        private const val CHUNK = 4096
        private const val PROGRESS_EVERY = 32L
        const val UNITY_TOLERANCE = 1e-3

        fun isUnity(rate: Double) = abs(rate - 1) < UNITY_TOLERANCE

        private fun ms(sampleRate: Int, millis: Int) = (sampleRate * millis / 1000.0).roundToInt()
    }
}
