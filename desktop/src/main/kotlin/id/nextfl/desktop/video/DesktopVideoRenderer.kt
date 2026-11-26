package id.nextfl.desktop.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import id.nextfl.app.visual.PulseRingTemplate
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.PcmSource
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import java.io.File
import java.io.IOException
import java.lang.foreign.MemorySegment
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.skia.Canvas as SkCanvas

/** Template visuals plus mix MP4. */
internal class DesktopVideoRenderer(
    private val width: Int,
    private val height: Int,
    private val audio: PcmSource,
    private val settings: VisualSettings,
    private val backgrounds: List<Bitmap>,
    private val logos: List<Bitmap>,
    private val typeface: Typeface,
    private val workers: Int = WORKERS,
) {
    private val analyzer = SpectrumAnalyzer(audio.sampleRate)
    private val window = FloatArray(analyzer.fftSize)
    // Window centered on frame time
    private val lookaheadFrames = analyzer.fftSize / 2
    private val pcm = ByteArray(AUDIO_CHUNK_FRAMES * BYTES_PER_FRAME)
    private var audioFrame = 0

    /** Returns false when cancelled. */
    fun render(output: File, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Boolean {
        if (audio.frames <= 0) throw IOException("Mixdown audio kosong")
        val total = ceil(audio.durationSeconds * FPS).toInt()
        MfSinkWriter(output, width, height, FPS, bitrate(), audio.sampleRate, AAC_BYTES_PER_SECOND).use { writer ->
            val done = renderFrames(total, isCancelled) { index, pixels, rowBytes ->
                val start = frameTime(index)
                writer.writeVideo(pixels, rowBytes, start, frameTime(index + 1) - start)
                writeAudio(writer, ((index + 1L) * audio.sampleRate / FPS).toInt())
                onProgress((index + 1f) / total)
            }
            if (!done) return false
            writeAudio(writer, audio.frames)
            writer.finish()
        }
        return true
    }

    /** Records in order, rasters in parallel. */
    internal fun renderFrames(total: Int, isCancelled: () -> Boolean, onFrame: (Int, MemorySegment, Int) -> Unit): Boolean {
        val template = PulseRingTemplate(typeface).apply { setImages(backgrounds, logos) }
        val pool = Executors.newFixedThreadPool(workers) { Thread(it, "video-raster").apply { isDaemon = true } }
        val recorder = PictureRecorder()
        val bounds = Rect.makeWH(width.toFloat(), height.toFloat())
        val frames = mutableListOf<Frame>()
        val free = ArrayDeque<Frame>()
        val pending = ArrayDeque<Job>()
        try {
            for (index in 0 until total) {
                if (isCancelled()) return false
                val picture = record(recorder, bounds, template, index)
                if (pending.size >= workers * IN_FLIGHT_PER_WORKER) free.addLast(emit(pending, onFrame))
                val frame = free.removeFirstOrNull() ?: Frame(width, height).also(frames::add)
                pending.addLast(Job(index, picture, frame, pool.submit { frame.draw(picture) }))
            }
            while (pending.isNotEmpty()) {
                if (isCancelled()) return false
                emit(pending, onFrame)
            }
            return true
        } finally {
            pool.shutdownNow()
            // Workers must stop first
            if (pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pending.forEach { it.picture.close() }
                frames.forEach(Frame::close)
            }
            recorder.close()
        }
    }

    // Template state advances here
    private fun record(recorder: PictureRecorder, bounds: Rect, template: PulseRingTemplate, index: Int): Picture {
        val canvas = Canvas(recorder.beginRecording(bounds))
        val sample = (index.toLong() * audio.sampleRate / FPS).toInt()
        val analysis = analyzer.analyze(fillWindow(sample + lookaheadFrames), DT, settings.sensitivity, settings.beatLow, settings.beatMid, settings.beatHigh)
        canvas.drawColor(Color.BLACK)
        template.draw(canvas, width, height, analysis, DT, settings)
        return recorder.finishRecordingAsPicture()
    }

    // Oldest frame, returns its bitmap
    private fun emit(pending: ArrayDeque<Job>, onFrame: (Int, MemorySegment, Int) -> Unit): Frame {
        val job = pending.first()
        try {
            job.task.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
        pending.removeFirst()
        job.picture.close()
        onFrame(job.index, job.frame.pixels, job.frame.rowBytes)
        return job.frame
    }

    // Mono mix like live tap
    private fun fillWindow(endExclusive: Int): FloatArray {
        val start = endExclusive - window.size
        for (i in window.indices) {
            val f = start + i
            window[i] = (audio.left(f) + audio.right(f)) * 0.5f
        }
        return window
    }

    // Feeds PCM up to frame
    private fun writeAudio(writer: MfSinkWriter, untilFrame: Int) {
        val target = min(untilFrame, audio.frames)
        while (audioFrame < target) {
            val count = min(target - audioFrame, AUDIO_CHUNK_FRAMES)
            var offset = 0
            for (i in 0 until count) {
                offset = putPcm(offset, audio.left(audioFrame + i))
                offset = putPcm(offset, audio.right(audioFrame + i))
            }
            val start = audioTime(audioFrame)
            audioFrame += count
            writer.writeAudio(pcm, offset, start, audioTime(audioFrame) - start)
        }
    }

    // NaN becomes silence
    private fun putPcm(offset: Int, value: Float): Int {
        val level = if (value.isNaN()) 0 else (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
        pcm[offset] = level.toByte()
        pcm[offset + 1] = (level shr 8).toByte()
        return offset + 2
    }

    private fun frameTime(index: Int): Long = index * HNS_PER_SECOND / FPS

    private fun audioTime(frame: Int): Long = frame * HNS_PER_SECOND / audio.sampleRate

    private fun bitrate(): Int = (width.toLong() * height * FPS * BITS_PER_PIXEL).toInt()

    private class Job(val index: Int, val picture: Picture, val frame: Frame, val task: Future<*>)

    /** Pooled raster target. */
    private class Frame(width: Int, height: Int) : AutoCloseable {
        private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val canvas = SkCanvas(bitmap.skia)
        private val pixmap = bitmap.skia.peekPixels() ?: throw IOException("Frame video tidak bisa dibaca")
        val rowBytes: Int = pixmap.rowBytes

        // N32 on Windows is BGRA
        val pixels: MemorySegment = MemorySegment.ofAddress(pixmap.addr).reinterpret(rowBytes.toLong() * height)

        fun draw(picture: Picture) {
            canvas.drawPicture(picture)
        }

        override fun close() {
            canvas.close()
            pixmap.close()
            bitmap.recycle()
        }
    }

    companion object {
        const val FPS = 30
        private const val DT = 1f / FPS
        private const val IN_FLIGHT_PER_WORKER = 2

        // Leave cores for encoder
        private val WORKERS = (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(1)
        private const val BITS_PER_PIXEL = 0.12

        // 192 kbps AAC
        private const val AAC_BYTES_PER_SECOND = 24_000
        private const val BYTES_PER_FRAME = 4
        private const val AUDIO_CHUNK_FRAMES = 4096
        private const val HNS_PER_SECOND = 10_000_000L
    }
}
