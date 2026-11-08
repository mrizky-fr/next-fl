package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioDecodeException
import id.nextfl.app.audio.DecodedAudio
import id.nextfl.app.platform.AudioDecoding
import id.nextfl.app.platform.PickedFile
import id.nextfl.core.pcm.PcmFileWriter
import id.nextfl.core.pcm.PeakAccumulator
import id.nextfl.core.pcm.TeeSink
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.DecoderException
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.coroutines.coroutineContext

/** MP3, WAV, AIFF; ffmpeg for the rest. */
class DesktopAudioDecoder(private val targetRate: Int) : AudioDecoding {

    override suspend fun decode(file: PickedFile, target: File, onProgress: (Float) -> Unit): DecodedAudio = withContext(Dispatchers.IO) {
        try {
            PcmFileWriter(target).use { writer ->
                val peaks = PeakAccumulator(targetRate)
                val feeder = PcmFeeder(TeeSink(writer, peaks), targetRate)
                when (file.file.extension.lowercase()) {
                    "mp3" -> decodeMp3(file.file, feeder, onProgress)
                    in SAMPLED -> decodeSampled(file.file, feeder, onProgress)
                    else -> Ffmpeg.decode(file.file, feeder, targetRate)
                }
                feeder.finish()
                if (writer.framesWritten == 0L) throw AudioDecodeException("Audio kosong atau rusak")
                DecodedAudio(writer.framesWritten.toInt(), peaks.build())
            }
        } catch (error: AudioDecodeException) {
            target.delete()
            throw error
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (error: Exception) {
            target.delete()
            throw AudioDecodeException(if (isStorageFull(error)) "Penyimpanan penuh" else "Format audio tidak didukung")
        }
    }

    private suspend fun decodeMp3(file: File, feeder: PcmFeeder, onProgress: (Float) -> Unit) {
        val counting = CountingStream(file.inputStream().buffered(BUFFER_BYTES))
        val bitstream = Bitstream(counting)
        val decoder = Decoder()
        val length = file.length().coerceAtLeast(1).toFloat()
        try {
            while (true) {
                coroutineContext.ensureActive()
                val header = bitstream.readFrame() ?: break
                try {
                    val output = decoder.decodeFrame(header, bitstream) as SampleBuffer
                    feeder.configure(output.sampleFrequency, output.channelCount)
                    feeder.shorts(output.buffer, output.bufferLength / output.channelCount)
                } catch (corrupt: DecoderException) {
                    // Skip one damaged frame
                }
                bitstream.closeFrame()
                onProgress(counting.count / length)
            }
        } finally {
            runCatching { bitstream.close() }
        }
    }

    private suspend fun decodeSampled(file: File, feeder: PcmFeeder, onProgress: (Float) -> Unit) {
        val stream = try {
            AudioSystem.getAudioInputStream(BufferedInputStream(file.inputStream(), BUFFER_BYTES))
        } catch (unsupported: UnsupportedAudioFileException) {
            return Ffmpeg.decode(file, feeder, targetRate)
        }
        stream.use { input ->
            val kind = SampleKind.of(input.format) ?: return Ffmpeg.decode(file, feeder, targetRate)
            readSampled(input, kind, feeder, onProgress)
        }
    }

    private suspend fun readSampled(input: AudioInputStream, kind: SampleKind, feeder: PcmFeeder, onProgress: (Float) -> Unit) {
        val format = input.format
        val channels = format.channels.coerceAtLeast(1)
        val frameBytes = format.frameSize
        val sampleBytes = format.sampleSizeInBits / BITS_PER_BYTE
        val order = if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val bytes = ByteArray(frameBytes * CHUNK_FRAMES)
        val buffer = ByteBuffer.wrap(bytes).order(order)
        val total = input.frameLength.takeIf { it > 0 }?.toFloat() ?: Float.MAX_VALUE
        feeder.configure(format.sampleRate.toInt(), channels)
        feeder.ensure(CHUNK_FRAMES)
        var done = 0L
        while (true) {
            coroutineContext.ensureActive()
            val frames = input.readNBytes(bytes, 0, bytes.size) / frameBytes
            if (frames <= 0) break
            for (i in 0 until frames) {
                val base = i * frameBytes
                val left = kind.read(buffer, base)
                feeder.left[i] = left
                feeder.right[i] = if (channels > 1) kind.read(buffer, base + sampleBytes) else left
            }
            feeder.push(frames)
            done += frames
            onProgress(done / total)
        }
    }

    private fun isStorageFull(error: Exception): Boolean =
        error is IOException && error.message.orEmpty().contains(NO_SPACE_TEXT, ignoreCase = true)

    /** Counts bytes for progress. */
    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    private enum class SampleKind {
        S8, U8, S16, S24, S32, F32, F64;

        fun read(buffer: ByteBuffer, at: Int): Float = when (this) {
            S8 -> buffer.get(at) / 128f
            U8 -> ((buffer.get(at).toInt() and 0xFF) - 128) / 128f
            S16 -> buffer.getShort(at) / 32768f
            S24 -> int24(buffer, at) / 8388608f
            S32 -> buffer.getInt(at) / 2147483648f
            F32 -> buffer.getFloat(at)
            F64 -> buffer.getDouble(at).toFloat()
        }

        companion object {
            fun of(format: AudioFormat): SampleKind? = when (format.encoding) {
                AudioFormat.Encoding.PCM_SIGNED -> when (format.sampleSizeInBits) {
                    8 -> S8
                    16 -> S16
                    24 -> S24
                    32 -> S32
                    else -> null
                }
                AudioFormat.Encoding.PCM_UNSIGNED -> if (format.sampleSizeInBits == 8) U8 else null
                AudioFormat.Encoding.PCM_FLOAT -> when (format.sampleSizeInBits) {
                    32 -> F32
                    64 -> F64
                    else -> null
                }
                else -> null
            }

            // Sign comes from high byte
            private fun int24(buffer: ByteBuffer, at: Int): Int {
                val little = buffer.order() == ByteOrder.LITTLE_ENDIAN
                val high = buffer.get(if (little) at + 2 else at).toInt()
                val middle = buffer.get(at + 1).toInt() and 0xFF
                val low = buffer.get(if (little) at else at + 2).toInt() and 0xFF
                return (high shl 16) or (middle shl 8) or low
            }
        }
    }

    private companion object {
        val SAMPLED = setOf("wav", "wave", "aif", "aiff", "aifc", "au")
        const val BUFFER_BYTES = 64 * 1024
        const val CHUNK_FRAMES = 4096
        const val BITS_PER_BYTE = 8
        const val NO_SPACE_TEXT = "not enough space"
    }
}
