package id.nextfl.core.pcm

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Mp3WriterTest {
    private val rate = 44_100

    private class Frames(val count: Int, val bitrates: Set<Int>, val sampleRates: Set<Int>)

    private fun encode(seconds: Double, bitrate: Int, signal: (Int) -> Float): ByteArray = encodeFrames((seconds * rate).toInt(), bitrate, signal)

    private fun encodeFrames(total: Int, bitrate: Int, signal: (Int) -> Float): ByteArray {
        val bytes = ByteArrayOutputStream()
        val scratch = File.createTempFile("mp3-test", ".mp3")
        Mp3Writer(bytes, rate, bitrate, scratch).use { writer ->
            val left = FloatArray(BLOCK)
            val right = FloatArray(BLOCK)
            var done = 0
            while (done < total) {
                val count = minOf(BLOCK, total - done)
                for (i in 0 until count) {
                    left[i] = signal(done + i)
                    right[i] = left[i]
                }
                writer.write(left, right, count)
                done += count
            }
        }
        assertFalse(scratch.exists(), "scratch file left behind")
        return bytes.toByteArray()
    }

    // Walks MPEG-1 Layer III headers
    private fun frames(data: ByteArray): Frames {
        var offset = 0
        var count = 0
        val bitrates = HashSet<Int>()
        val sampleRates = HashSet<Int>()
        while (offset + HEADER_BYTES <= data.size) {
            val first = data[offset].toInt() and BYTE_MASK
            val second = data[offset + 1].toInt() and BYTE_MASK
            val third = data[offset + 2].toInt() and BYTE_MASK
            assertTrue(first == BYTE_MASK && (second and SYNC_MASK) == SYNC_MASK, "lost sync at $offset")
            assertEquals(MPEG1, (second shr 3) and 3, "MPEG version at $offset")
            assertEquals(LAYER3, (second shr 1) and 3, "layer at $offset")
            val bitrate = BITRATES[(third shr 4) and 0xF]
            val sampleRate = SAMPLE_RATES[(third shr 2) and 3]
            val padding = (third shr 1) and 1
            bitrates += bitrate
            sampleRates += sampleRate
            offset += FRAME_FACTOR * bitrate * 1000 / sampleRate + padding
            count++
        }
        return Frames(count, bitrates, sampleRates)
    }

    private fun ByteArray.indexOfSequence(target: ByteArray): Int {
        for (start in 0..size - target.size) {
            if (target.indices.all { this[start + it] == target[it] }) return start
        }
        return -1
    }

    private fun tone(index: Int) = (0.5 * sin(2 * PI * 440 * index / rate)).toFloat()

    // Tone plus seeded noise
    private fun music(index: Int): Float {
        val noise = ((index * NOISE_MULTIPLIER + NOISE_INCREMENT) % NOISE_MODULUS).toFloat() / NOISE_MODULUS - 0.5f
        return tone(index) * 0.6f + noise * 0.3f
    }

    // Repetitive frames compress well
    private fun compressedShare(data: ByteArray): Double {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(data.size + DEFLATE_SLACK)
        val size = deflater.deflate(buffer)
        deflater.end()
        return size.toDouble() / data.size
    }

    @Test
    fun writesValidConstantBitrateStream() {
        val frames = frames(encode(2.0, 192, ::tone))
        assertEquals(setOf(192), frames.bitrates)
        assertEquals(setOf(rate), frames.sampleRates)
        val seconds = frames.count * SAMPLES_PER_FRAME.toDouble() / rate
        assertTrue(seconds in 2.0..2.2, "duration $seconds")
    }

    // Players trim delay and padding
    @Test
    fun gaplessTagMatchesInputLength() {
        val samples = rate * 3 / 2 + ODD_TAIL
        val data = encodeFrames(samples, 192, ::tone)
        val lame = data.indexOfSequence("LAME".toByteArray())
        assertTrue(data.indexOfSequence("Info".toByteArray()) in 0 until lame, "Info tag missing")
        val packed = (0 until 3).fold(0) { value, i -> (value shl 8) or (data[lame + DELAY_OFFSET + i].toInt() and BYTE_MASK) }
        val delay = packed shr 12
        val padding = packed and 0xFFF
        val audioFrames = frames(data).count - 1
        assertTrue(delay in 500..1500, "delay $delay")
        assertEquals(samples, audioFrames * SAMPLES_PER_FRAME - delay - padding, "delay $delay padding $padding")
    }

    @Test
    fun toneCarriesAudioDataSilenceDoesNot() {
        val audio = compressedShare(encode(1.0, 128, ::music))
        val silence = compressedShare(encode(1.0, 128) { 0f })
        assertTrue(audio > silence * 3, "audio $audio silence $silence")
    }

    @Test
    fun invalidSamplesDoNotCrash() {
        val data = encode(0.5, 320) { if (it % 7 == 0) Float.NaN else if (it % 11 == 0) Float.POSITIVE_INFINITY else tone(it) }
        assertEquals(setOf(320), frames(data).bitrates)
    }

    @Test
    fun encodesFasterThanRealtime() {
        val started = System.nanoTime()
        encode(20.0, 320, ::tone)
        val seconds = (System.nanoTime() - started) / 1e9
        println("MP3 320k: 20 s audio in $seconds s")
        assertTrue(seconds < 20.0, "encode took $seconds s")
    }

    private companion object {
        const val BLOCK = 1024
        const val HEADER_BYTES = 4
        const val BYTE_MASK = 0xFF
        const val SYNC_MASK = 0xE0
        const val MPEG1 = 3
        const val LAYER3 = 1
        const val FRAME_FACTOR = 144
        const val SAMPLES_PER_FRAME = 1152
        const val DEFLATE_SLACK = 64
        const val ODD_TAIL = 123
        const val DELAY_OFFSET = 21
        const val NOISE_MULTIPLIER = 1_103_515_245L
        const val NOISE_INCREMENT = 12_345L
        const val NOISE_MODULUS = 65_536L
        val BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        val SAMPLE_RATES = intArrayOf(44_100, 48_000, 32_000, 0)
    }
}
