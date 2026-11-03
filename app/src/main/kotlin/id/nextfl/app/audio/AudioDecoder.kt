package id.nextfl.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import id.nextfl.core.pcm.PcmFileWriter
import id.nextfl.core.pcm.PeakAccumulator
import id.nextfl.core.pcm.StereoSink
import id.nextfl.core.pcm.StreamingResampler
import id.nextfl.core.pcm.TeeSink
import id.nextfl.core.pcm.WaveformPeaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class AudioDecodeException(message: String) : Exception(message)

class DecodedAudio(val frames: Int, val peaks: WaveformPeaks)

/** Decodes files to engine PCM. */
class AudioDecoder(private val context: Context, private val targetRate: Int) {

    suspend fun decode(uri: Uri, target: File, onProgress: (Float) -> Unit): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioDecodeException("File ini tidak berisi audio")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > MAX_DURATION_US) throw AudioDecodeException("Durasi maksimal 30 menit")
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw AudioDecodeException("Format tidak dikenali")
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            PcmFileWriter(target).use { writer ->
                val peaks = PeakAccumulator(targetRate)
                val pipeline = Pipeline(TeeSink(writer, peaks), targetRate)
                pump(extractor, codec, pipeline, durationUs, onProgress)
                pipeline.finish()
                if (writer.framesWritten == 0L) throw AudioDecodeException("Audio kosong atau rusak")
                DecodedAudio(writer.framesWritten.toInt(), peaks.build())
            }
        } catch (error: AudioDecodeException) {
            target.delete()
            throw error
        } catch (error: Exception) {
            target.delete()
            if (error is kotlinx.coroutines.CancellationException) throw error
            throw AudioDecodeException("Format audio tidak didukung")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private suspend fun pump(extractor: MediaExtractor, codec: MediaCodec, pipeline: Pipeline, durationUs: Long, onProgress: (Float) -> Unit) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            coroutineContext.ensureActive()
            if (!inputDone) inputDone = feedInput(extractor, codec)
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> pipeline.configure(codec.outputFormat)
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        if (!pipeline.configured) pipeline.configure(codec.outputFormat)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        pipeline.consume(buffer)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun feedInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val buffer = codec.getInputBuffer(index) ?: return false
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /** Codec output to stereo floats. */
    private class Pipeline(private val output: StereoSink, private val targetRate: Int) {
        private var channels = 2
        private var floatPcm = false
        private var sink: StereoSink = output
        private var resampler: StreamingResampler? = null
        private var left = FloatArray(CHUNK)
        private var right = FloatArray(CHUNK)
        var configured = false
            private set

        fun configure(format: MediaFormat) {
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            resampler?.finish()
            resampler = if (rate != targetRate) StreamingResampler(rate, targetRate, output) else null
            sink = resampler ?: output
            configured = true
        }

        fun consume(buffer: ByteBuffer) {
            buffer.order(ByteOrder.nativeOrder())
            val bytesPerSample = if (floatPcm) 4 else 2
            val frames = buffer.remaining() / (bytesPerSample * channels)
            if (frames > left.size) {
                left = FloatArray(frames)
                right = FloatArray(frames)
            }
            if (floatPcm) readFloat(buffer, frames) else readShort(buffer, frames)
            sink.write(left, right, frames)
        }

        fun finish() = resampler?.finish()

        private fun readShort(buffer: ByteBuffer, frames: Int) {
            val shorts = buffer.asShortBuffer()
            for (i in 0 until frames) {
                val base = i * channels
                left[i] = shorts.get(base) / 32768f
                right[i] = if (channels > 1) shorts.get(base + 1) / 32768f else left[i]
            }
        }

        private fun readFloat(buffer: ByteBuffer, frames: Int) {
            val floats = buffer.asFloatBuffer()
            for (i in 0 until frames) {
                val base = i * channels
                left[i] = floats.get(base)
                right[i] = if (channels > 1) floats.get(base + 1) else left[i]
            }
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_DURATION_US = 30L * 60 * 1_000_000
        const val CHUNK = 8192
    }
}
