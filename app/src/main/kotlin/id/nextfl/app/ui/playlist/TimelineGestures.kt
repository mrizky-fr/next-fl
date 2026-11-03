package id.nextfl.app.ui.playlist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import id.nextfl.core.edit.ClipOps
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.edit.projectEndSeconds
import id.nextfl.core.state.EditorState
import id.nextfl.core.util.Beats
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.SnapMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

private sealed interface DragMode {
    data object Pan : DragMode
    data object Scrub : DragMode
    data class Move(val clipId: String, val grabOffset: Double) : DragMode
    data class Trim(val clipId: String, val atStart: Boolean) : DragMode
}

/** Playlist canvas touch logic. */
class TimelineGestures(
    private val vm: EditorViewModel,
    private val geometry: TimelineGeometry,
    private val scope: CoroutineScope,
) {
    var snapGuide by mutableStateOf<Double?>(null)
        private set
    private var state: EditorState = vm.editor.value
    private var snap = SnapMode.BEAT
    private var width = 0f
    private var height = 0f
    private var fling: Job? = null
    private var interacting = false
    private val viewport get() = vm.viewport

    fun bind(state: EditorState, snap: SnapMode, width: Float, height: Float) {
        this.state = state
        this.snap = snap
        this.width = width
        this.height = height
    }

    suspend fun PointerInputScope.handleTouches() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fling?.cancel()
            interacting = true
            val mode = start(down.position)
            val tracker = VelocityTracker().apply { addPointerInputChange(down) }
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    finish(mode, moved)
                    pinch()
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!moved) tap(mode, down.position)
                    finish(mode, moved)
                    if (moved && mode == DragMode.Pan) startFling(tracker.calculateVelocity().let { Offset(it.x, it.y) })
                    break
                }
                tracker.addPointerInputChange(change)
                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    if (mode is DragMode.Move || mode is DragMode.Trim) vm.beginGesture()
                }
                if (moved) drag(mode, change)
                change.consume()
            }
            interacting = false
        }
    }

    suspend fun PointerInputScope.handleWheel() {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.first()
                val delta = change.scrollDelta
                if (event.keyboardModifiers.isCtrlPressed) {
                    viewport.zoomAround(change.position.x, exp(-delta.y * WHEEL_ZOOM), density)
                } else {
                    scrollBy(-(delta.y + delta.x) * WHEEL_PIXELS, 0f)
                }
                change.consume()
            }
        }
    }

    fun followPlayhead(position: Double) {
        if (interacting || width <= 0f) return
        val x = geometry.timeToX(position, viewport.scrollSeconds, viewport.pixelsPerSecond)
        if (x > width * FOLLOW_EDGE || x < 0) {
            viewport.scrollSeconds = (position - width * FOLLOW_LEAD / viewport.pixelsPerSecond).coerceAtLeast(0.0)
        }
    }

    private fun start(position: Offset): DragMode {
        val hit = geometry.hitTest(state.doc, state.assets, position.x, position.y, viewport.scrollSeconds, viewport.scrollY, viewport.pixelsPerSecond)
        return when (hit) {
            is TimelineHit.Ruler -> DragMode.Scrub.also { vm.seek(hit.time) }
            is TimelineHit.ClipEdge -> DragMode.Trim(hit.clipId, hit.atStart).also { vm.selectClip(hit.clipId) }
            is TimelineHit.ClipBody -> {
                vm.selectClip(hit.clipId)
                val clip = state.doc.clips.first { it.id == hit.clipId }
                DragMode.Move(hit.clipId, hit.time - Beats.toSeconds(clip.startBeat, state.doc.bpm))
            }
            is TimelineHit.Empty -> DragMode.Pan
        }
    }

    private fun tap(mode: DragMode, position: Offset) {
        if (mode != DragMode.Pan) return
        vm.selectClip(null)
        val index = geometry.trackIndexAt(position.y, viewport.scrollY)
        state.doc.tracks.getOrNull(index)?.let { vm.selectTrack(it.id) }
    }

    private fun drag(mode: DragMode, change: PointerInputChange) {
        val time = geometry.xToTime(change.position.x, viewport.scrollSeconds, viewport.pixelsPerSecond)
        when (mode) {
            DragMode.Pan -> (change.position - change.previousPosition).let { scrollBy(it.x, it.y) }
            DragMode.Scrub -> vm.seek(time.coerceAtLeast(0.0))
            is DragMode.Move -> moveClip(mode, time, change.position.y)
            is DragMode.Trim -> trimClip(mode, time)
        }
    }

    private fun moveClip(mode: DragMode.Move, time: Double, y: Float) {
        val doc = vm.editor.value.doc
        val start = TimelineGeometry.snap(time - mode.grabOffset, doc.bpm, snap)
        val index = geometry.trackIndexAt(y, viewport.scrollY).coerceIn(0, doc.tracks.lastIndex)
        val trackId = doc.tracks[index].id
        snapGuide = start
        vm.change { current -> ClipOps.update(current, mode.clipId) { it.copy(startBeat = Beats.fromSeconds(start, current.bpm), trackId = trackId) } }
    }

    private fun trimClip(mode: DragMode.Trim, time: Double) {
        val current = vm.editor.value
        val clip = current.doc.clips.find { it.id == mode.clipId } ?: return
        val asset = current.assets[clip.assetId] ?: return
        val edge = TimelineGeometry.snap(time, current.doc.bpm, snap)
        val context = ClipOps.TrimContext(clipSpan(clip, current.assets, current.doc.bpm).rate, current.doc.bpm, asset.duration)
        snapGuide = edge
        vm.change { doc ->
            ClipOps.update(doc, mode.clipId) { if (mode.atStart) ClipOps.trimStart(it, edge, context) else ClipOps.trimEnd(it, edge, context) }
        }
    }

    private fun finish(mode: DragMode, moved: Boolean) {
        snapGuide = null
        if (moved && (mode is DragMode.Move || mode is DragMode.Trim)) vm.endGesture()
    }

    private suspend fun AwaitPointerEventScope.pinch() {
        var previous: Pair<Offset, Float>? = null
        while (true) {
            val pressed = awaitPointerEvent().changes.filter { it.pressed }
            if (pressed.size < 2) return
            val a = pressed[0].position
            val b = pressed[1].position
            val center = (a + b) / 2f
            val distance = hypot(a.x - b.x, a.y - b.y).coerceAtLeast(1f)
            previous?.let { (lastCenter, lastDistance) ->
                viewport.zoomAround(center.x, distance / lastDistance, density)
                scrollBy(center.x - lastCenter.x, center.y - lastCenter.y)
            }
            previous = center to distance
            pressed.forEach { it.consume() }
        }
    }

    private fun startFling(velocity: Offset) {
        fling = scope.launch {
            var vx = velocity.x
            var vy = velocity.y
            var last = withFrameNanos { it }
            while (abs(vx) > MIN_FLING || abs(vy) > MIN_FLING) {
                withFrameNanos { now ->
                    val dt = (now - last) / 1e9f
                    last = now
                    scrollBy(vx * dt, vy * dt)
                    val decay = exp(-FRICTION * dt)
                    vx *= decay
                    vy *= decay
                }
            }
        }
    }

    // Positive dx drags content right
    private fun scrollBy(dx: Float, dy: Float) {
        val pps = viewport.pixelsPerSecond
        val maxSeconds = maxOf(projectEndSeconds(state.doc, state.assets) + TAIL_SECONDS, width / pps.toDouble())
        viewport.scrollSeconds = (viewport.scrollSeconds - dx / pps).coerceIn(0.0, maxSeconds)
        viewport.scrollY = (viewport.scrollY - dy).coerceIn(0f, geometry.maxScrollY(state.doc.tracks.size, height))
    }

    private companion object {
        const val FOLLOW_EDGE = 0.85f
        const val FOLLOW_LEAD = 0.1f
        const val WHEEL_ZOOM = 0.15f
        const val WHEEL_PIXELS = 40f
        const val MIN_FLING = 40f
        const val FRICTION = 4f
        const val TAIL_SECONDS = 60.0
    }
}
