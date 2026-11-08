package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioDecodeException
import id.nextfl.app.platform.PickedFile
import id.nextfl.core.pcm.MappedPcmSource
import id.nextfl.core.pcm.Mp3Writer
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopAudioDecoderTest {
    private val rate = 44_100
    private val dir = createTempDirectory("decoder").toFile()
    private val decoder = DesktopAudioDecoder(rate)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun tone(index: Int, sampleRate: Int) = (0.5 * sin(2 * PI * 440 * index / sampleRate)).toFloat()

    // 24-bit stereo at another rate
    private fun wav48k24(seconds: Double): File {
        val sourceRate = 48_000
        val frames = (seconds * sourceRate).toInt()
        val bytes = ByteBuffer.allocate(frames * 6).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val value = (tone(i, sourceRate) * 8_388_607).toInt()
            repeat(2) {
                bytes.put(value.toByte())
                bytes.put((value shr 8).toByte())
                bytes.put((value shr 16).toByte())
            }
        }
        val format = AudioFormat(sourceRate.toFloat(), 24, 2, true, false)
        val file = File(dir, "tone.wav")
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes.array()), format, frames.toLong()), AudioFileFormat.Type.WAVE, file)
        return file
    }

    private fun mp3(seconds: Double): File {
        val file = File(dir, "tone.mp3")
        Mp3Writer(file.outputStream(), rate, 192, File(dir, "scratch.mp3")).use { writer ->
            val total = (seconds * rate).toInt()
            val left = FloatArray(total) { tone(it, rate) }
            writer.write(left, left.copyOf(), total)
        }
        return file
    }

    private fun decodedSeconds(file: File): Pair<Double, Float> = runBlocking {
        val target = File(dir, "out.pcm")
        val decoded = decoder.decode(PickedFile(file), target) {}
        MappedPcmSource(target, rate).use { source ->
            // Peak of one second mid-file
            val peak = (rate / 2 until rate * 3 / 2).maxOf { abs(source.left(it)) }
            decoded.frames.toDouble() / rate to peak
        }
    }

    @Test
    fun wavIsResampledToEngineRate() {
        val (seconds, peak) = decodedSeconds(wav48k24(2.0))
        assertTrue(abs(seconds - 2.0) < 0.01, "duration $seconds")
        assertTrue(peak in 0.45f..0.55f, "peak $peak")
    }

    @Test
    fun mp3DecodesWithJLayer() {
        val (seconds, peak) = decodedSeconds(mp3(2.0))
        assertTrue(abs(seconds - 2.0) < 0.1, "duration $seconds")
        assertTrue(peak in 0.4f..0.6f, "peak $peak")
    }

    @Test
    fun garbageIsRejectedCleanly() {
        val file = File(dir, "broken.wav").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
        val target = File(dir, "out.pcm")
        assertFailsWith<AudioDecodeException> { runBlocking { decoder.decode(PickedFile(file), target) {} } }
        assertTrue(!target.exists(), "partial output left behind")
    }
}
