package id.nextfl.core.pcm

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val BYTES_PER_FRAME = 4
private const val CHUNK_FRAMES = 4096
private const val FULL_SCALE = 32768f
private const val MAX_CODE = 32767
private const val MIN_CODE = -32768

/** Triangular dither, one LSB. */
internal class TpdfDither {
    private var state = SEED

    fun next(): Float = uniform() - uniform()

    // Xorshift32, allocation free
    private fun uniform(): Float {
        state = state xor (state shl XOR_A)
        state = state xor (state ushr XOR_B)
        state = state xor (state shl XOR_C)
        return (state ushr MANTISSA_SHIFT) * UNIT
    }

    private companion object {
        const val SEED = 0x2545F491
        const val XOR_A = 13
        const val XOR_B = 17
        const val XOR_C = 5
        const val MANTISSA_SHIFT = 8
        const val UNIT = 1f / (1 shl 24)
    }
}

/** Streams 16-bit stereo frames. */
open class Pcm16StreamWriter(output: OutputStream, dither: Boolean = false) : StereoSink, Closeable {
    private val out = BufferedOutputStream(output, 1 shl 16)
    private val bytes = ByteArray(CHUNK_FRAMES * BYTES_PER_FRAME)
    private val ditherer = if (dither) TpdfDither() else null
    var framesWritten = 0L
        private set

    override fun write(left: FloatArray, right: FloatArray, count: Int) {
        var done = 0
        while (done < count) {
            val chunk = minOf(CHUNK_FRAMES, count - done)
            for (i in 0 until chunk) {
                putFrame(i * BYTES_PER_FRAME, quantize(left[done + i]), quantize(right[done + i]))
            }
            out.write(bytes, 0, chunk * BYTES_PER_FRAME)
            done += chunk
        }
        framesWritten += count
    }

    protected fun writeRaw(data: ByteArray) = out.write(data)

    // Mirrors decoder scale; NaN safe
    private fun quantize(value: Float): Int {
        val noise = if (value == 0f) 0f else ditherer?.next() ?: 0f
        return Math.round(value * FULL_SCALE + noise).coerceIn(MIN_CODE, MAX_CODE)
    }

    override fun close() = out.close()

    private fun putFrame(at: Int, left: Int, right: Int) {
        bytes[at] = left.toByte()
        bytes[at + 1] = (left shr 8).toByte()
        bytes[at + 2] = right.toByte()
        bytes[at + 3] = (right shr 8).toByte()
    }
}

class PcmFileWriter(file: File, dither: Boolean = false) : Pcm16StreamWriter(FileOutputStream(file), dither)

/** WAV with known length. */
class WavWriter(output: OutputStream, private val sampleRate: Int, private val totalFrames: Long) :
    Pcm16StreamWriter(output, dither = true) {

    init {
        writeRaw(header(sampleRate, totalFrames))
    }

    override fun close() {
        val missing = (totalFrames - framesWritten).toInt()
        if (missing > 0) {
            val silence = FloatArray(missing)
            write(silence, silence, missing)
        }
        super.close()
    }

    companion object {
        const val HEADER_BYTES = 44

        fun header(sampleRate: Int, frames: Long): ByteArray {
            val dataBytes = frames * BYTES_PER_FRAME
            val header = ByteArray(HEADER_BYTES)
            fun ascii(at: Int, text: String) = text.forEachIndexed { i, c -> header[at + i] = c.code.toByte() }
            fun int32(at: Int, value: Long) = repeat(4) { header[at + it] = (value shr (8 * it)).toByte() }
            fun int16(at: Int, value: Int) = repeat(2) { header[at + it] = (value shr (8 * it)).toByte() }
            ascii(0, "RIFF")
            int32(4, HEADER_BYTES - 8 + dataBytes)
            ascii(8, "WAVE")
            ascii(12, "fmt ")
            int32(16, 16)
            int16(20, 1)
            int16(22, 2)
            int32(24, sampleRate.toLong())
            int32(28, sampleRate.toLong() * BYTES_PER_FRAME)
            int16(32, BYTES_PER_FRAME)
            int16(34, 16)
            ascii(36, "data")
            int32(40, dataBytes)
            return header
        }
    }
}
