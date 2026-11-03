package id.nextfl.core.pcm

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel

/** Random-access stereo audio source. */
interface PcmSource {
    val sampleRate: Int
    val frames: Int
    fun left(frame: Int): Float
    fun right(frame: Int): Float

    val durationSeconds: Double get() = frames.toDouble() / sampleRate
}

fun interface StereoSink {
    fun write(left: FloatArray, right: FloatArray, count: Int)
}

class ArrayPcmSource(
    private val leftData: FloatArray,
    private val rightData: FloatArray,
    override val sampleRate: Int,
) : PcmSource {
    override val frames: Int = leftData.size

    override fun left(frame: Int): Float = if (frame in 0 until frames) leftData[frame] else 0f

    override fun right(frame: Int): Float = if (frame in 0 until frames) rightData[frame] else 0f
}

// 16-bit interleaved stereo, little endian
class MappedPcmSource(file: File, override val sampleRate: Int) : PcmSource, Closeable {
    private val access = RandomAccessFile(file, "r")
    private val samples: ShortBuffer = access.channel
        .map(FileChannel.MapMode.READ_ONLY, 0, access.length())
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()

    override val frames: Int = samples.capacity() / 2

    override fun left(frame: Int): Float = if (frame in 0 until frames) samples.get(frame * 2) * SCALE else 0f

    override fun right(frame: Int): Float = if (frame in 0 until frames) samples.get(frame * 2 + 1) * SCALE else 0f

    override fun close() = access.close()

    private companion object {
        const val SCALE = 1f / 32768f
    }
}

/** In-memory sink for tests. */
class ArrayStereoSink : StereoSink {
    private var left = FloatArray(1024)
    private var right = FloatArray(1024)
    var size = 0
        private set

    override fun write(left: FloatArray, right: FloatArray, count: Int) {
        ensure(size + count)
        left.copyInto(this.left, size, 0, count)
        right.copyInto(this.right, size, 0, count)
        size += count
    }

    fun toSource(sampleRate: Int) = ArrayPcmSource(left.copyOf(size), right.copyOf(size), sampleRate)

    private fun ensure(capacity: Int) {
        if (capacity <= left.size) return
        val next = maxOf(capacity, left.size * 2)
        left = left.copyOf(next)
        right = right.copyOf(next)
    }
}
