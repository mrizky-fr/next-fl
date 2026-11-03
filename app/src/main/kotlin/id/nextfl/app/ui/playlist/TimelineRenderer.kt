package id.nextfl.app.ui.playlist

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import id.nextfl.core.edit.ClipSpan
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.Clip
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.pcm.WaveformPeaks
import id.nextfl.core.util.AudioMath
import id.nextfl.core.util.Beats
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TimelineScene(
    val width: Float,
    val height: Float,
    val scroll: Double,
    val scrollY: Float,
    val pps: Float,
    val doc: ProjectDoc,
    val assets: AssetMap,
    val selectedClipId: String?,
    val selectedTrackId: String?,
    val playhead: Double,
    val snapGuide: Double?,
    val peaks: (String) -> WaveformPeaks?,
    val isStretching: (Clip, Double) -> Boolean,
)

/** Draws playlist via android.graphics. */
class TimelineRenderer(private val geometry: TimelineGeometry, private val density: Float, typeface: Typeface) {
    private val metrics = geometry.metrics
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        this.typeface = typeface
    }
    private val rect = RectF()
    private val path = Path()
    private var lines = FloatArray(4096)

    fun draw(canvas: Canvas, scene: TimelineScene) {
        fill.color = BACKGROUND
        canvas.drawRect(0f, 0f, scene.width, scene.height, fill)
        drawRows(canvas, scene)
        drawGrid(canvas, scene)
        val (selected, others) = scene.doc.clips.partition { it.id == scene.selectedClipId }
        (others + selected).forEach { drawClip(canvas, scene, it) }
        drawRuler(canvas, scene)
        scene.snapGuide?.let { drawVertical(canvas, scene, it, SNAP, 1f) }
        drawPlayhead(canvas, scene)
        if (scene.doc.clips.isEmpty()) drawEmptyHint(canvas, scene)
    }

    private fun drawRows(canvas: Canvas, scene: TimelineScene) {
        scene.doc.tracks.forEachIndexed { index, track ->
            val top = geometry.rowTop(index, scene.scrollY)
            if (top > scene.height || top + metrics.rowHeight < metrics.rulerHeight) return@forEachIndexed
            fill.color = when {
                track.id == scene.selectedTrackId -> ROW_SELECTED
                index % 2 == 0 -> ROW_EVEN
                else -> ROW_ODD
            }
            canvas.drawRect(0f, top, scene.width, top + metrics.rowHeight, fill)
            fill.color = ROW_LINE
            canvas.drawRect(0f, top + metrics.rowHeight - density, scene.width, top + metrics.rowHeight, fill)
        }
    }

    private fun drawGrid(canvas: Canvas, scene: TimelineScene) {
        val step = TimelineGeometry.gridStepBeats(scene.pps, scene.doc.bpm, metrics.minGridPx)
        val startBeat = floor(Beats.fromSeconds(scene.scroll, scene.doc.bpm) / step) * step
        val barPx = scene.pps * Beats.toSeconds(Beats.PER_BAR.toDouble(), scene.doc.bpm)
        var beat = startBeat
        while (true) {
            val x = geometry.timeToX(Beats.toSeconds(beat, scene.doc.bpm), scene.scroll, scene.pps)
            if (x > scene.width) break
            val barIndex = floor(beat / Beats.PER_BAR).toLong()
            if (barPx > metrics.minGridPx && barIndex % 2 == 1L && beat % Beats.PER_BAR == 0.0) {
                fill.color = BAR_SHADE
                canvas.drawRect(x, metrics.rulerHeight, x + barPx.toFloat(), scene.height, fill)
            }
            fill.color = when {
                beat % Beats.PER_BAR == 0.0 -> GRID_BAR
                beat % 1.0 == 0.0 -> GRID_BEAT
                else -> GRID_SUB
            }
            canvas.drawRect(x, metrics.rulerHeight, x + density, scene.height, fill)
            beat += step
        }
    }

    private fun drawClip(canvas: Canvas, scene: TimelineScene, clip: Clip) {
        val index = scene.doc.tracks.indexOfFirst { it.id == clip.trackId }
        if (index < 0) return
        val span = clipSpan(clip, scene.assets, scene.doc.bpm)
        val left = geometry.timeToX(span.start, scene.scroll, scene.pps)
        val right = geometry.timeToX(span.end, scene.scroll, scene.pps)
        val top = geometry.rowTop(index, scene.scrollY) + 2 * density
        val bottom = top + metrics.rowHeight - 4 * density
        if (right < 0 || left > scene.width || bottom < metrics.rulerHeight || top > scene.height) return
        val color = scene.doc.tracks[index].color.toInt()
        val radius = 3 * density

        canvas.save()
        rect.set(left, top, right, bottom)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        fill.color = withAlpha(color, 0.2f)
        canvas.drawRect(rect, fill)
        fill.color = withAlpha(color, 0.9f)
        canvas.drawRect(left, top, right, top + metrics.clipHeader, fill)
        drawWaveform(canvas, scene, clip, span, left, top + metrics.clipHeader, bottom, color)
        drawFades(canvas, clip, span, scene.pps, left, right, top + metrics.clipHeader, bottom)
        drawClipLabel(canvas, scene, clip, span, left, right, top)
        canvas.restore()

        val selected = clip.id == scene.selectedClipId
        stroke.strokeWidth = if (selected) 2 * density else density
        stroke.color = if (selected) SELECTED_OUTLINE else withAlpha(color, 0.95f)
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, stroke)
        if (selected) drawHandles(canvas, left, right, top, bottom)
    }

    private fun drawWaveform(canvas: Canvas, scene: TimelineScene, clip: Clip, span: ClipSpan, left: Float, top: Float, bottom: Float, color: Int) {
        val peaks = scene.peaks(clip.assetId) ?: return
        val mid = (top + bottom) / 2
        val halfHeight = (bottom - top) / 2 * WAVE_FILL
        val gain = min(MAX_WAVE_GAIN, AudioMath.dbToGain(clip.gainDb))
        val first = max(0, -left.toInt())
        val last = min(ceil((span.duration * scene.pps).toDouble()).toInt(), (scene.width - left).toInt())
        if (last <= first) return
        ensureLines((last - first) * 4)
        var count = 0
        val blocksPerSecond = peaks.blocksPerSecond
        for (column in first until last) {
            val from = clip.offset + column / scene.pps * span.rate
            val to = clip.offset + (column + 1) / scene.pps * span.rate
            val startBlock = max(0, floor(from * blocksPerSecond).toInt())
            val endBlock = min(peaks.max.size, max(startBlock + 1, ceil(to * blocksPerSecond).toInt()))
            var low = 0f
            var high = 0f
            for (block in startBlock until endBlock) {
                low = min(low, peaks.min[block])
                high = max(high, peaks.max[block])
            }
            val x = left + column + 0.5f
            lines[count++] = x
            lines[count++] = mid - (high * gain).coerceIn(-1f, 1f) * halfHeight
            lines[count++] = x
            lines[count++] = mid - (low * gain).coerceIn(-1f, 1f) * halfHeight + 1
        }
        stroke.strokeWidth = density.coerceAtMost(1.5f)
        stroke.color = lighten(color, 0.45f)
        canvas.drawLines(lines, 0, count, stroke)
    }

    private fun drawFades(canvas: Canvas, clip: Clip, span: ClipSpan, pps: Float, left: Float, right: Float, top: Float, bottom: Float) {
        fill.color = FADE_SHADE
        stroke.color = FADE_LINE
        stroke.strokeWidth = 1.5f * density
        if (clip.fadeIn > 0) {
            val end = left + (min(clip.fadeIn, span.duration) * pps).toFloat()
            path.reset(); path.moveTo(left, top); path.lineTo(end, top); path.lineTo(left, bottom); path.close()
            canvas.drawPath(path, fill)
            canvas.drawLine(left, bottom, end, top, stroke)
        }
        if (clip.fadeOut > 0) {
            val start = right - (min(clip.fadeOut, span.duration) * pps).toFloat()
            path.reset(); path.moveTo(start, top); path.lineTo(right, top); path.lineTo(right, bottom); path.close()
            canvas.drawPath(path, fill)
            canvas.drawLine(start, top, right, bottom, stroke)
        }
    }

    private fun drawClipLabel(canvas: Canvas, scene: TimelineScene, clip: Clip, span: ClipSpan, left: Float, right: Float, top: Float) {
        text.color = Color.WHITE
        val baseline = top + metrics.clipHeader - 4 * density
        canvas.drawText(clip.name, max(left, 0f) + 6 * density, baseline, text)
        val badge = when {
            scene.isStretching(clip, span.rate) -> "stretch…"
            abs(span.rate - 1) > 1e-3 -> String.format(Locale.ROOT, "×%.2f", span.rate)
            else -> null
        } ?: return
        val width = text.measureText(badge)
        text.color = Color.argb(220, 255, 255, 255)
        canvas.drawText(badge, min(right, scene.width) - width - 6 * density, baseline, text)
    }

    private fun drawHandles(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        fill.color = HANDLE
        val handleHeight = (bottom - top) * 0.4f
        val y = top + (bottom - top - handleHeight) / 2 + metrics.clipHeader / 2
        rect.set(left + 3 * density, y, left + 7 * density, y + handleHeight)
        canvas.drawRoundRect(rect, 2 * density, 2 * density, fill)
        rect.set(right - 7 * density, y, right - 3 * density, y + handleHeight)
        canvas.drawRoundRect(rect, 2 * density, 2 * density, fill)
    }

    private fun drawRuler(canvas: Canvas, scene: TimelineScene) {
        fill.color = RULER
        canvas.drawRect(0f, 0f, scene.width, metrics.rulerHeight, fill)
        fill.color = ROW_LINE
        canvas.drawRect(0f, metrics.rulerHeight - density, scene.width, metrics.rulerHeight, fill)
        val bpm = scene.doc.bpm
        val barSeconds = Beats.toSeconds(Beats.PER_BAR.toDouble(), bpm)
        var every = 1
        while (barSeconds * every * scene.pps < 36 * density) every *= 2
        var bar = floor(scene.scroll / barSeconds / every).toInt() * every
        text.color = RULER_TEXT
        while (true) {
            val x = geometry.timeToX(bar * barSeconds, scene.scroll, scene.pps)
            if (x > scene.width) break
            fill.color = GRID_BAR
            canvas.drawRect(x, metrics.rulerHeight * 0.55f, x + density, metrics.rulerHeight, fill)
            canvas.drawText((bar + 1).toString(), x + 4 * density, metrics.rulerHeight * 0.62f, text)
            bar += every
        }
    }

    private fun drawPlayhead(canvas: Canvas, scene: TimelineScene) {
        val x = geometry.timeToX(scene.playhead, scene.scroll, scene.pps)
        if (x < -10 || x > scene.width + 10) return
        drawVertical(canvas, scene, scene.playhead, PLAYHEAD, 1.5f)
        path.reset()
        path.moveTo(x - 6 * density, 0f)
        path.lineTo(x + 6 * density, 0f)
        path.lineTo(x, 9 * density)
        path.close()
        fill.color = PLAYHEAD
        canvas.drawPath(path, fill)
    }

    private fun drawVertical(canvas: Canvas, scene: TimelineScene, time: Double, color: Int, widthDp: Float) {
        val x = geometry.timeToX(time, scene.scroll, scene.pps)
        fill.color = color
        canvas.drawRect(x - widthDp * density / 2, 0f, x + widthDp * density / 2, scene.height, fill)
    }

    private fun drawEmptyHint(canvas: Canvas, scene: TimelineScene) {
        text.color = EMPTY_TEXT
        val message = "Ketuk \"+ Audio\" untuk menambahkan lagu"
        val width = text.measureText(message)
        canvas.drawText(message, (scene.width - width) / 2, metrics.rulerHeight + metrics.rowHeight * 1.5f, text)
    }

    private fun ensureLines(size: Int) {
        if (lines.size < size) lines = FloatArray(size * 2)
    }

    private companion object {
        const val WAVE_FILL = 0.92f
        const val MAX_WAVE_GAIN = 2f
        val BACKGROUND = Color.parseColor("#131517")
        val ROW_EVEN = Color.parseColor("#17191C")
        val ROW_ODD = Color.parseColor("#131517")
        val ROW_SELECTED = Color.parseColor("#1E2024")
        val ROW_LINE = Color.parseColor("#1C1E21")
        val GRID_BAR = Color.parseColor("#2C2F34")
        val GRID_BEAT = Color.parseColor("#212327")
        val GRID_SUB = Color.parseColor("#1A1C1F")
        val BAR_SHADE = Color.argb(4, 255, 255, 255)
        val RULER = Color.parseColor("#0D0E10")
        val RULER_TEXT = Color.parseColor("#A1A1AA")
        val PLAYHEAD = Color.WHITE
        val SNAP = Color.parseColor("#6366F1")
        val SELECTED_OUTLINE = Color.WHITE
        val HANDLE = Color.argb(220, 255, 255, 255)
        val FADE_SHADE = Color.argb(90, 0, 0, 0)
        val FADE_LINE = Color.argb(180, 255, 255, 255)
        val EMPTY_TEXT = Color.parseColor("#71717A")

        fun withAlpha(color: Int, alpha: Float) = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        fun lighten(color: Int, amount: Float): Int {
            fun mix(channel: Int) = (channel + (255 - channel) * amount).toInt()
            return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
        }
    }
}
