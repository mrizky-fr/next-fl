package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioDecodeException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Optional ffmpeg on PATH for other formats. */
object Ffmpeg {
    suspend fun decode(file: File, feeder: PcmFeeder, rate: Int) {
        val process = try {
            ProcessBuilder(
                "ffmpeg", "-v", "error", "-nostdin", "-i", file.absolutePath,
                "-f", "f32le", "-acodec", "pcm_f32le", "-ac", "2", "-ar", rate.toString(), "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (missing: IOException) {
            throw AudioDecodeException("Format ini belum didukung. Pakai MP3 atau WAV")
        }
        try {
            feeder.configure(rate, STEREO)
            feeder.ensure(CHUNK_FRAMES)
            val bytes = ByteArray(CHUNK_FRAMES * FRAME_BYTES)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            process.inputStream.use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val frames = input.readNBytes(bytes, 0, bytes.size) / FRAME_BYTES
                    if (frames <= 0) break
                    for (i in 0 until frames) {
                        feeder.left[i] = buffer.getFloat(i * FRAME_BYTES)
                        feeder.right[i] = buffer.getFloat(i * FRAME_BYTES + SAMPLE_BYTES)
                    }
                    feeder.push(frames)
                }
            }
            if (process.waitFor() != 0) throw AudioDecodeException("Format audio tidak didukung")
        } finally {
            process.destroy()
        }
    }

    private const val STEREO = 2
    private const val SAMPLE_BYTES = 4
    private const val FRAME_BYTES = 8
    private const val CHUNK_FRAMES = 4096
}
