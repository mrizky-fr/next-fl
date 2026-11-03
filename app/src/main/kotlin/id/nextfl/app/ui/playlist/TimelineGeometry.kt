package id.nextfl.app.ui.playlist

import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.util.Beats
import id.nextfl.app.editor.SnapMode
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Density-based pixel sizes. */
class TimelineMetrics(density: Float) {
    val rowHeight = 64f * density
    val rulerHeight = 26f * density
    val clipHeader = 15f * density
    val edgeHandle = 18f * density
    val minGridPx = 12f * density
    val trackHeaderWidth = 132f * density
    val playheadGrab = 18f * density

    // Keeps playhead handle fully visible
    val originX = 10f * density
}

sealed interface TimelineHit {
    data class Ruler(val time: Double) : TimelineHit
    data class ClipBody(val clipId: String, val time: Double) : TimelineHit
    data class ClipEdge(val clipId: String, val atStart: Boolean) : TimelineHit
    data class Empty(val trackIndex: Int, val time: Double) : TimelineHit
}

class TimelineGeometry(val metrics: TimelineMetrics) {
    fun timeToX(time: Double, scroll: Double, pps: Float): Float = ((time - scroll) * pps).toFloat() + metrics.originX

    fun xToTime(x: Float, scroll: Double, pps: Float): Double = scroll + (x - metrics.originX) / pps

    fun rowTop(index: Int, scrollY: Float): Float = metrics.rulerHeight + index * metrics.rowHeight - scrollY

    fun trackIndexAt(y: Float, scrollY: Float): Int = floor((y - metrics.rulerHeight + scrollY) / metrics.rowHeight).toInt()

    fun maxScrollY(trackCount: Int, viewHeight: Float): Float =
        max(0f, metrics.rulerHeight + (trackCount + 1) * metrics.rowHeight - viewHeight)

    fun hitTest(doc: ProjectDoc, assets: AssetMap, x: Float, y: Float, scroll: Double, scrollY: Float, pps: Float): TimelineHit {
        val time = xToTime(x, scroll, pps)
        if (y < metrics.rulerHeight) return TimelineHit.Ruler(time)
        val index = trackIndexAt(y, scrollY)
        val trackId = doc.tracks.getOrNull(index)?.id
        val clip = doc.clips.lastOrNull { clip ->
            if (clip.trackId != trackId) return@lastOrNull false
            val span = clipSpan(clip, assets, doc.bpm)
            x >= timeToX(span.start, scroll, pps) && x <= timeToX(span.end, scroll, pps)
        } ?: return TimelineHit.Empty(index, time)
        val span = clipSpan(clip, assets, doc.bpm)
        val left = timeToX(span.start, scroll, pps)
        val right = timeToX(span.end, scroll, pps)
        val edge = min(metrics.edgeHandle, (right - left) * EDGE_SHARE)
        return when {
            x - left <= edge -> TimelineHit.ClipEdge(clip.id, atStart = true)
            right - x <= edge -> TimelineHit.ClipEdge(clip.id, atStart = false)
            else -> TimelineHit.ClipBody(clip.id, time)
        }
    }

    companion object {
        private const val EDGE_SHARE = 0.3f
        private val GRID_STEPS = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0, 128.0)

        fun snap(seconds: Double, bpm: Double, mode: SnapMode): Double {
            if (mode == SnapMode.OFF) return max(0.0, seconds)
            val beats = (Beats.fromSeconds(seconds, bpm) / mode.beats).roundToLong() * mode.beats
            return max(0.0, Beats.toSeconds(beats, bpm))
        }

        fun gridStepBeats(pps: Float, bpm: Double, minPx: Float): Double {
            val beatPx = pps * 60.0 / bpm
            return GRID_STEPS.firstOrNull { it * beatPx >= minPx } ?: GRID_STEPS.last()
        }
    }
}
