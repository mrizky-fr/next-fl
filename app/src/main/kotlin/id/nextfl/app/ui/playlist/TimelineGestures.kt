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
import androidx.compose.ui.input.pointer.PointerId
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
import kotlin.math.roundToInt

private sealed interface DragMode {
    data object Pan : DragMode
    data object Scrub : DragMode
    data class Playhead(val grabOffset: Double) : DragMode
    data class Move(val clipId: String, val grabOffset: Double, val trackIndex: Int, val downY: Float) : DragMode
    data class Trim(val clipId: String, val atStart: Boolean) : DragMode
}

private val DragMode.editsDoc: Boolean get() = this is DragMode.Move || this is DragMode.Trim

/** Playlist canvas touch logic. */
class TimelineGestures(
    private val vm: EditorViewModel,
    private val geometry: TimelineGeometry,
    private val scope: CoroutineScope,
) {
    var snapGuide by mutableStateOf<Double?>(null)
        private set

    // Shown instead of audible position
    var scrubPosition by mutableStateOf<Double?>(null)
        private set
    var playheadGrabbed by mutableStateOf(false)
        private set
    private var state: EditorState = vm.editor.value
    private var snap = SnapMode.BEAT
    private var width = 0f
    private var height = 0f

    // Follow owns horizontal scroll
    private var lockPan = false
    private var fling: Job? = null
    private var autoScroll: Job? = null
    private var settle: Job? = null
    private var settleTarget = 0f
    private var pointer = Offset.Zero
    private var interacting = false
    private val viewport get() = vm.viewport
    private val metrics get() = geometry.metrics

    // Mode Ngonten shows one row
    private val singleLane get() = height - metrics.rulerHeight < SINGLE_LANE_ROWS * metrics.rowHeight

    fun bind(state: EditorState, snap: SnapMode, width: Float, height: Float, lockPan: Boolean) {
        this.state = state
        this.lockPan = lockPan
        this.snap = snap
        this.width = width
        this.height = height
    }

    suspend fun PointerInputScope.handleTouches() {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            stopMotion()
            interacting = true
            val mode = start(down.position)
            val tracker = VelocityTracker().apply { addPointerInputChange(down) }
            var moved = false
            var release: Offset? = null
            // Finally keeps undo history consistent
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        finish(mode, moved)
                        moved = false
                        pinch()
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!moved) tap(mode, down.position)
                        if (moved && mode == DragMode.Pan) release = tracker.calculateVelocity().let { Offset(it.x, it.y) }
                        break
                    }
                    tracker.addPointerInputChange(change)
                    if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                        if (mode.editsDoc) vm.beginGesture()
                    }
                    if (moved) drag(mode, change.position, change.previousPosition)
                    change.consume()
                }
            } finally {
                finish(mode, moved)
                interacting = false
            }
            val velocity = release
            if (velocity != null) startFling(velocity)
            settleLanes()
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
                    zoomAt(change.position.x, exp(-delta.y * WHEEL_ZOOM), density)
                } else {
                    if (!lockPan) scrollBy(-(delta.y + delta.x) * WHEEL_PIXELS, 0f)
                }
                change.consume()
            }
        }
    }

    // Playhead parks after 5 bars, grid scrolls
    fun followPlayhead(position: Double) {
        if ((interacting && !lockPan) || width <= 0f) return
        val pps = viewport.pixelsPerSecond
        val barsWidth = Beats.toSeconds(FOLLOW_BARS * Beats.PER_BAR.toDouble(), state.doc.bpm) * pps
        val anchor = minOf(barsWidth.toFloat(), width * MAX_ANCHOR_SHARE)
        val x = geometry.timeToX(position, viewport.scrollSeconds, pps) - metrics.originX
        if (x > anchor || x < 0f) viewport.scrollSeconds = (position - anchor / pps).coerceAtLeast(0.0)
    }

    private fun start(position: Offset): DragMode {
        grabPlayhead(position)?.let { return it }
        val hit = geometry.hitTest(state.doc, state.assets, position.x, position.y, viewport.scrollSeconds, viewport.scrollY, viewport.pixelsPerSecond, state.selectedClipId)
        return when (hit) {
            is TimelineHit.Ruler -> DragMode.Scrub.also { scrubTo(hit.time) }
            is TimelineHit.ClipEdge -> DragMode.Trim(hit.clipId, hit.atStart).also { vm.selectClip(hit.clipId) }
            is TimelineHit.ClipBody -> grabClip(hit, position.y)
            is TimelineHit.Empty -> DragMode.Pan
        }
    }

    private fun grabClip(hit: TimelineHit.ClipBody, y: Float): DragMode {
        val doc = state.doc
        val clip = doc.clips.find { it.id == hit.clipId } ?: return DragMode.Pan
        vm.selectClip(clip.id)
        val trackIndex = doc.tracks.indexOfFirst { it.id == clip.trackId }
        return DragMode.Move(clip.id, hit.time - Beats.toSeconds(clip.startBeat, doc.bpm), trackIndex, y)
    }

    // Handle drags relative, no jump
    private fun grabPlayhead(position: Offset): DragMode? {
        if (position.y >= metrics.rulerHeight) return null
        val playhead = vm.position()
        val x = geometry.timeToX(playhead, viewport.scrollSeconds, viewport.pixelsPerSecond)
        if (abs(position.x - x) > metrics.playheadGrab) return null
        playheadGrabbed = true
        scrubPosition = playhead
        return DragMode.Playhead(geometry.xToTime(position.x, viewport.scrollSeconds, viewport.pixelsPerSecond) - playhead)
    }

    private fun scrubTo(time: Double) {
        val target = time.coerceAtLeast(0.0)
        scrubPosition = target
        vm.seek(target)
    }

    private fun tap(mode: DragMode, position: Offset) {
        if (mode != DragMode.Pan) return
        vm.selectClip(null)
        val index = geometry.trackIndexAt(position.y, viewport.scrollY)
        state.doc.tracks.getOrNull(index)?.let { vm.selectTrack(it.id) }
    }

    private fun drag(mode: DragMode, position: Offset, previous: Offset) {
        if (mode == DragMode.Pan) {
            scrollBy(if (lockPan) 0f else position.x - previous.x, position.y - previous.y)
            return
        }
        applyAt(mode, position)
        followEdges(mode, position)
    }

    private fun applyAt(mode: DragMode, position: Offset) {
        val time = geometry.xToTime(position.x, viewport.scrollSeconds, viewport.pixelsPerSecond)
        when (mode) {
            DragMode.Pan -> Unit
            DragMode.Scrub -> scrubTo(time)
            is DragMode.Playhead -> scrubTo(time - mode.grabOffset)
            is DragMode.Move -> moveClip(mode, time, position.y)
            is DragMode.Trim -> trimClip(mode, time)
        }
    }

    private fun moveClip(mode: DragMode.Move, time: Double, y: Float) {
        val doc = vm.editor.value.doc
        if (doc.tracks.isEmpty()) return
        val start = TimelineGeometry.snap(time - mode.grabOffset, doc.bpm, snap)
        val index = targetTrack(mode, y, doc.tracks.lastIndex)
        val trackId = doc.tracks[index].id
        snapGuide = start
        vm.change { current -> ClipOps.move(current, mode.clipId, trackId, Beats.fromSeconds(start, current.bpm)) }
        if (singleLane) revealRow(index)
    }

    // Lane mode maps finger travel
    private fun targetTrack(mode: DragMode.Move, y: Float, lastIndex: Int): Int {
        val index = if (singleLane) {
            mode.trackIndex + ((y - mode.downY) / metrics.laneStep).toInt()
        } else {
            geometry.trackIndexAt(y, viewport.scrollY)
        }
        return index.coerceIn(0, lastIndex)
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
        autoScroll?.cancel()
        snapGuide = null
        scrubPosition = null
        playheadGrabbed = false
        if (!moved || !mode.editsDoc) return
        vm.endGesture()
        if (mode is DragMode.Move) vm.selectClip(mode.clipId)
    }

    private fun stopMotion() {
        fling?.cancel()
        settle?.cancel()
        autoScroll?.cancel()
    }

    // Drag near edges scrolls view
    private fun followEdges(mode: DragMode, position: Offset) {
        pointer = position
        if (autoScroll?.isActive == true || isStill(edgeVelocity(mode, position))) return
        autoScroll = scope.launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val velocity = edgeVelocity(mode, pointer)
                if (isStill(velocity)) break
                val dt = frameSeconds(now - last)
                last = now
                scrollBy(-velocity.x * dt, -velocity.y * dt)
                applyAt(mode, pointer)
            }
        }
    }

    private fun edgeVelocity(mode: DragMode, position: Offset): Offset {
        val zone = metrics.autoScrollZone
        val vx = if (lockPan) 0f else edgeShare(position.x, zone, width - zone, zone) * metrics.autoScrollSpeedX
        val vertical = mode is DragMode.Move && !singleLane
        val zoneY = metrics.autoScrollZoneY
        val vy = if (vertical) edgeShare(position.y, metrics.rulerHeight + zoneY, height - zoneY, zoneY) * metrics.autoScrollSpeedY else 0f
        return Offset(vx, vy)
    }

    // Signed depth into edge zone
    private fun edgeShare(value: Float, low: Float, high: Float, ramp: Float): Float = when {
        high <= low -> 0f
        value < low -> -((low - value) / ramp).coerceAtMost(1f)
        value > high -> ((value - high) / ramp).coerceAtMost(1f)
        else -> 0f
    }

    private fun isStill(velocity: Offset) = velocity.x == 0f && velocity.y == 0f

    // Anchor relative to time origin
    private fun zoomAt(x: Float, factor: Float, density: Float) = viewport.zoomAround(x - metrics.originX, factor, density)

    private suspend fun AwaitPointerEventScope.pinch() {
        var previous: Pair<Offset, Float>? = null
        var fingers: Pair<PointerId, PointerId>? = null
        while (true) {
            val pressed = awaitPointerEvent().changes.filter { it.pressed }
            if (pressed.size < 2) return
            val a = pressed[0]
            val b = pressed[1]
            val center = (a.position + b.position) / 2f
            val distance = (a.position - b.position).getDistance().coerceAtLeast(1f)
            val ids = a.id to b.id
            // Finger swap resets, no jump
            if (ids == fingers) previous?.let { (lastCenter, lastDistance) ->
                zoomAt(center.x, distance / lastDistance, density)
                scrollBy(if (lockPan) 0f else center.x - lastCenter.x, center.y - lastCenter.y)
            }
            fingers = ids
            previous = center to distance
            pressed.forEach { it.consume() }
        }
    }

    private fun startFling(velocity: Offset) {
        fling = scope.launch {
            var vx = if (lockPan) 0f else velocity.x
            var vy = if (singleLane) 0f else velocity.y
            var last = withFrameNanos { it }
            while (abs(vx) > MIN_FLING || abs(vy) > MIN_FLING) {
                withFrameNanos { now ->
                    val dt = frameSeconds(now - last)
                    last = now
                    scrollBy(vx * dt, vy * dt)
                    val decay = exp(-FRICTION * dt)
                    vx *= decay
                    vy *= decay
                }
            }
            settleLanes()
        }
    }

    // Single lane rests on rows
    private fun settleLanes() {
        if (!singleLane || settle?.isActive == true) return
        val row = metrics.rowHeight
        settleTo((viewport.scrollY / row).roundToInt() * row)
    }

    private fun revealRow(index: Int) {
        val target = clampScrollY(index * metrics.rowHeight)
        if (settle?.isActive == true && settleTarget == target) return
        settleTo(target)
    }

    private fun settleTo(target: Float) {
        settle?.cancel()
        settleTarget = clampScrollY(target)
        settle = scope.launch {
            var last = withFrameNanos { it }
            while (abs(settleTarget - viewport.scrollY) > SETTLE_EPSILON) {
                val now = withFrameNanos { it }
                val dt = frameSeconds(now - last)
                last = now
                viewport.scrollY += (settleTarget - viewport.scrollY) * (1f - exp(-SETTLE_RATE * dt))
            }
            viewport.scrollY = settleTarget
        }
    }

    private fun frameSeconds(nanos: Long): Float = (nanos / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)

    // Positive dx drags content right
    private fun scrollBy(dx: Float, dy: Float) {
        val pps = viewport.pixelsPerSecond
        if (pps <= 0f) return
        val maxSeconds = maxOf(projectEndSeconds(state.doc, state.assets) + TAIL_SECONDS, width / pps.toDouble())
        viewport.scrollSeconds = (viewport.scrollSeconds - dx / pps).coerceIn(0.0, maxSeconds)
        viewport.scrollY = clampScrollY(viewport.scrollY - dy)
    }

    private fun clampScrollY(value: Float): Float = value.coerceIn(0f, geometry.maxScrollY(state.doc.tracks.size, height))

    private companion object {
        const val FOLLOW_BARS = 5
        const val MAX_ANCHOR_SHARE = 0.5f
        const val WHEEL_ZOOM = 0.15f
        const val WHEEL_PIXELS = 40f
        const val MIN_FLING = 40f
        const val FRICTION = 4f
        const val TAIL_SECONDS = 60.0
        const val SINGLE_LANE_ROWS = 1.5f
        const val SETTLE_RATE = 18f
        const val SETTLE_EPSILON = 0.5f
        const val NANOS_PER_SECOND = 1e9f
        const val MAX_FRAME_SECONDS = 0.05f
    }
}
