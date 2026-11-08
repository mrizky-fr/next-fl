package id.nextfl.desktop.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.fromBytes
import id.nextfl.app.ui.playlist.TimelineGeometry
import id.nextfl.app.ui.playlist.TimelineMetrics
import id.nextfl.app.ui.playlist.TimelineRenderer
import id.nextfl.app.ui.playlist.TimelineScene
import id.nextfl.app.visual.PulseRingTemplate
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.ArrayPcmSource
import id.nextfl.core.pcm.WaveformPeaks
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Draws shared android.graphics code via Skia. */
class OffscreenRenderTest {
    private val out = File("build/offscreen").apply { mkdirs() }
    private val typeface = Typeface.fromBytes(javaClass.classLoader.getResourceAsStream("inter_bold.ttf")!!.readBytes())

    private fun save(bitmap: Bitmap, name: String) {
        val data = bitmap.image().encodeToData(EncodedImageFormat.PNG)!!
        File(out, name).writeBytes(data.bytes)
    }

    // Counts pixels unlike the background
    private fun litPixels(bitmap: Bitmap, background: Int): Int {
        var lit = 0
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) if (bitmap.getPixel(x, y) != background) lit++
        return lit
    }

    @Test
    fun pulseRingDrawsOnBeat() {
        val width = 1280
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val template = PulseRingTemplate(typeface)
        val settings = VisualSettings(title = "NextFL Desktop", showSocial = true, socialUsername = "@djkyy", glitchAmount = 0f)
        val frame = AudioFrame()
        repeat(FRAMES) { i ->
            val onBeat = i % BEAT_FRAMES == 0
            frame.beat = onBeat
            frame.beatStrength = if (onBeat) 1f else 0f
            frame.kick = if (i % BEAT_FRAMES < 6) 0.9f else 0f
            frame.bass = if (i % BEAT_FRAMES < 10) 0.8f else 0.1f
            frame.energy = 0.5f
            template.draw(canvas, width, height, frame, 1f / 60, settings)
        }
        save(bitmap, "pulse-ring.png")
        assertTrue(litPixels(bitmap, Color.BLACK) > 1000, "frame looks empty")
    }

    @Test
    fun timelineDrawsClipWaveform() {
        val density = 1.5f
        val geometry = TimelineGeometry(TimelineMetrics(density))
        val renderer = TimelineRenderer(geometry, density, typeface)
        val rate = 44_100
        val samples = FloatArray(rate * 8) { (sin(2 * PI * 2 * it / rate) * sin(2 * PI * 110 * it / rate) * 0.8).toFloat() }
        val peaks = WaveformPeaks.compute(ArrayPcmSource(samples, samples, rate))
        val asset = AudioAsset("ast", "Lagu Uji", 8.0, 128.0)
        val base = Defaults.project()
        val clip = Defaults.clip(base.tracks[1].id, asset.id, asset.name, 0.0, asset.duration)
        val doc = base.copy(clips = listOf(clip))
        val bitmap = Bitmap.createBitmap(1600, 500, Bitmap.Config.ARGB_8888)
        val scene = TimelineScene(
            width = 1600f, height = 500f, scroll = 0.0, scrollY = 0f, pps = 150f, doc = doc, assets = mapOf(asset.id to asset),
            selectedClipId = clip.id, selectedTrackId = clip.trackId, playhead = 2.5, playheadGrabbed = false, snapGuide = null,
            peaks = { peaks },
        )
        renderer.draw(Canvas(bitmap), scene)
        save(bitmap, "timeline.png")
        assertTrue(litPixels(bitmap, bitmap.getPixel(5, 499)) > 1000, "timeline looks empty")
    }

    private companion object {
        const val FRAMES = 40
        const val BEAT_FRAMES = 28
    }
}
