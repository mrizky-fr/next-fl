package id.nextfl.desktop.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import id.nextfl.app.visual.PulseRingTemplate
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.PcmSource
import java.io.File
import java.io.IOException
import java.lang.foreign.MemorySegment
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Template visuals plus mix MP4. */
internal class DesktopVideoRenderer(
    private val width: Int,
    private val height: Int,
    private val audio: PcmSource,
    private val settings: VisualSettings,
    private val backgrounds: List<Bitmap>,
    private val logos: List<Bitmap>,
    private val typeface: Typeface,
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
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixmap = frame.skia.peekPixels() ?: throw IOException("Frame video tidak bisa dibaca")
        try {
            // N32 on Windows is BGRA
            val pixels = MemorySegment.ofAddress(pixmap.addr).reinterpret(pixmap.rowBytes.toLong() * height)
            MfSinkWriter(output, width, height, FPS, bitrate(), audio.sampleRate, AAC_BYTES_PER_SECOND).use { writer ->
                if (!renderFrames(writer, Canvas(frame), pixels, pixmap.rowBytes, onProgress, isCancelled)) return false
                writeAudio(writer, audio.frames)
                writer.finish()
            }
            return true
        } finally {
            pixmap.close()
            frame.recycle()
        }
    }

    private fun renderFrames(
        writer: MfSinkWriter,
        canvas: Canvas,
        pixels: MemorySegment,
        rowBytes: Int,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val template = PulseRingTemplate(typeface).apply { setImages(backgrounds, logos) }
        val total = ceil(audio.durationSeconds * FPS).toInt()
        val dt = 1f / FPS
        for (index in 0 until total) {
            if (isCancelled()) return false
            val sample = (index.toLong() * audio.sampleRate / FPS).toInt()
            val analysis = analyzer.analyze(fillWindow(sample + lookaheadFrames), dt, settings.sensitivity, settings.beatLow, settings.beatMid, settings.beatHigh)
            canvas.drawColor(Color.BLACK)
            template.draw(canvas, width, height, analysis, dt, settings)
            val start = frameTime(index)
            writer.writeVideo(pixels, rowBytes, start, frameTime(index + 1) - start)
            writeAudio(writer, ((index + 1L) * audio.sampleRate / FPS).toInt())
            onProgress((index + 1f) / total)
        }
        return true
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

    companion object {
        const val FPS = 30
        private const val BITS_PER_PIXEL = 0.12

        // 192 kbps AAC
        private const val AAC_BYTES_PER_SECOND = 24_000
        private const val BYTES_PER_FRAME = 4
        private const val AUDIO_CHUNK_FRAMES = 4096
        private const val HNS_PER_SECOND = 10_000_000L
    }
}
