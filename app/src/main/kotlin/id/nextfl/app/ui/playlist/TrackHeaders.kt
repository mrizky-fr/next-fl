package id.nextfl.app.ui.playlist

import kotlin.math.log10
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import id.nextfl.app.editor.TimelineViewport
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import id.nextfl.core.edit.TrackOps
import id.nextfl.app.ui.components.rememberFrameTick
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.Track
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.ColorSwatches
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.LocalControlScale
import id.nextfl.app.ui.components.buttonLabelSize
import id.nextfl.app.ui.components.ModalDialog
import id.nextfl.app.ui.components.TextInput
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import kotlin.math.roundToInt

private const val ROW_DP = 64
private const val ROW_GAP_DP = 1
private const val DRAG_Z = 1f
private const val SINGLE_LANE_ROWS = 1.5f
private const val FLICK_VELOCITY = 600f
private const val HEADER_TOGGLE_SCALE = 0.5f
private const val LABEL_BOOST = 1.4f
private val TOGGLE_TOUCH_PAD = 4.dp

/** Long-press drag of one header. */
private data class TrackReorder(val trackId: String, val fromIndex: Int, val offset: Float = 0f, val travel: Float = 0f)

@Composable
fun TrackHeaders(vm: EditorViewModel, tracks: List<Track>, selectedTrackId: String?, geometry: TimelineGeometry, playing: Boolean) {
    var editing by remember { mutableStateOf<Track?>(null) }
    var listHeight by remember { mutableFloatStateOf(0f) }
    var reorder by remember { mutableStateOf<TrackReorder?>(null) }
    val haptics = LocalHapticFeedback.current
    val editSlop = LocalViewConfiguration.current.touchSlop
    val viewport = vm.viewport
    val maxScroll = geometry.maxScrollY(tracks.size, listHeight + geometry.metrics.rulerHeight)
    // Drag list, shares timeline scroll
    val scrollState = rememberScrollableState { delta ->
        val before = viewport.scrollY
        viewport.scrollY = (before - delta).coerceIn(0f, maxScroll)
        before - viewport.scrollY
    }
    val defaultFling = ScrollableDefaults.flingBehavior()
    val rowPx = geometry.metrics.rowHeight
    val laneFling = remember(defaultFling, rowPx) {
        LaneSnapFling(rowPx, viewport, defaultFling) { listHeight < SINGLE_LANE_ROWS * rowPx }
    }
    LaunchedEffect(maxScroll) { viewport.scrollY = viewport.scrollY.coerceIn(0f, maxScroll) }
    Column(Modifier.width(132.dp).fillMaxHeight().background(Palette.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).background(Palette.Well).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TRACKS", style = TextStyles.Label, modifier = Modifier.weight(1f))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { listHeight = it.height.toFloat() }
                .scrollable(scrollState, Orientation.Vertical, flingBehavior = laneFling),
        ) {
            // Unbounded so offscreen rows keep height
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .offset { IntOffset(0, -viewport.scrollY.roundToInt()) },
            ) {
                tracks.forEachIndexed { index, track ->
                    TrackHeaderRow(
                        track = track,
                        selected = track.id == selectedTrackId,
                        playing = playing,
                        dragOffset = reorder?.takeIf { it.trackId == track.id }?.offset,
                        level = { vm.trackLevel(track.id) },
                        onSelect = { vm.selectTrack(track.id) },
                        onEdit = { editing = track },
                        onMute = { vm.updateTrack(track.id) { it.copy(mute = !it.mute) } },
                        onSolo = { vm.updateTrack(track.id) { it.copy(solo = !it.solo) } },
                        reorder = HeaderDrag(
                            onStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorder = TrackReorder(track.id, index)
                            },
                            onDrag = { dy -> reorder = reorder?.let { it.copy(offset = it.offset + dy, travel = it.travel + abs(dy)) } },
                            onEnd = {
                                reorder?.let { drag -> finishReorder(vm, drag, geometry.metrics.rowHeight, editSlop) { editing = track } }
                                reorder = null
                            },
                            onCancel = { reorder = null },
                        ),
                    )
                }
                FlatButton("Tambah track", Icons.Rounded.Add, vm::addTrack, Modifier.fillMaxWidth().padding(6.dp))
            }
        }
    }
    editing?.let { track -> TrackEditDialog(vm, track) { editing = null } }
}

/** Single lane fling rests on rows. */
private class LaneSnapFling(
    private val rowPx: Float,
    private val viewport: TimelineViewport,
    private val fallback: FlingBehavior,
    private val singleLane: () -> Boolean,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (!singleLane()) return with(fallback) { this@performFling.performFling(initialVelocity) }
        val rows = viewport.scrollY / rowPx
        val index = when {
            initialVelocity > FLICK_VELOCITY -> floor(rows)
            initialVelocity < -FLICK_VELOCITY -> ceil(rows)
            else -> round(rows)
        }
        var last = viewport.scrollY
        animate(last, index * rowPx) { value, _ ->
            scrollBy(last - value)
            last = value
        }
        return 0f
    }
}

/** Header reorder callbacks. */
private class HeaderDrag(
    val onStart: () -> Unit,
    val onDrag: (Float) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

// Rounded row travel picks slot
private fun finishReorder(vm: EditorViewModel, drag: TrackReorder, rowPx: Float, editSlop: Float, onEdit: () -> Unit) {
    if (drag.travel < editSlop) return onEdit()
    val target = drag.fromIndex + (drag.offset / rowPx).roundToInt()
    vm.change { TrackOps.move(it, drag.trackId, target) }
}

// Row height matches timeline rows
@Composable
private fun TrackHeaderRow(
    track: Track,
    selected: Boolean,
    playing: Boolean,
    dragOffset: Float?,
    level: () -> Float,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    reorder: HeaderDrag,
) {
    val drag by rememberUpdatedState(reorder)
    Row(
        modifier = Modifier
            .zIndex(if (dragOffset != null) DRAG_Z else 0f)
            .offset { IntOffset(0, (dragOffset ?: 0f).roundToInt()) }
            .fillMaxWidth()
            .height((ROW_DP - ROW_GAP_DP).dp)
            .background(if (selected || dragOffset != null) Palette.Card else Palette.Surface)
            .pointerInput(track.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { drag.onStart() },
                    onDragEnd = { drag.onEnd() },
                    onDragCancel = { drag.onCancel() },
                ) { _, amount -> drag.onDrag(amount.y) }
            }
            .combinedClickable(onClick = onSelect, onDoubleClick = onEdit)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrackColorStrip(Color(track.color), playing, level)
        Column(Modifier.weight(1f)) {
            Text(track.name, style = TextStyles.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Tahan untuk edit", style = TextStyles.Caption.copy(fontSize = 9.sp, color = Palette.TextMuted), maxLines = 1)
        }
        Column {
            SmallToggle("M", track.mute, Palette.Mute, scale = HEADER_TOGGLE_SCALE, touchPadding = TOGGLE_TOUCH_PAD, onClick = onMute)
            SmallToggle("S", track.solo, Palette.Solo, scale = HEADER_TOGGLE_SCALE, touchPadding = TOGGLE_TOUCH_PAD, onClick = onSolo)
        }
    }
    Box(Modifier.fillMaxWidth().height(ROW_GAP_DP.dp))
}

// Strip flickers with heard level
@Composable
private fun TrackColorStrip(color: Color, playing: Boolean, level: () -> Float) {
    val tick by rememberFrameTick(active = playing)
    val glow = remember { StripGlow() }
    Canvas(Modifier.width(4.dp).height(40.dp)) {
        val brightness = if (playing) glow.update(level(), tick) else 1f
        drawRoundRect(color.copy(alpha = brightness), cornerRadius = CornerRadius(Dimens.RadiusSmall.toPx()))
    }
}

/** Brightness relative to recent loudest. */
private class StripGlow {
    private var value = DIM
    private var ceiling = FLOOR_DB + RANGE_DB
    private var lastNanos = 0L

    fun update(peak: Float, nanos: Long): Float {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, MAX_STEP_SECONDS)
        lastNanos = nanos
        val db = if (peak > 0f) 20f * log10(peak) else FLOOR_DB
        ceiling = maxOf(db, ceiling - CEILING_FALL_DB * dt, FLOOR_DB + RANGE_DB)
        val target = if (db <= FLOOR_DB) DIM else DIM + (1f - DIM) * ((db - ceiling + RANGE_DB) / RANGE_DB).coerceIn(0f, 1f)
        value = maxOf(target, value - FADE_PER_SECOND * dt)
        return value
    }

    private companion object {
        const val DIM = 0.25f
        const val FLOOR_DB = -48f
        const val RANGE_DB = 9f
        const val CEILING_FALL_DB = 6f
        const val FADE_PER_SECOND = 6f
        const val MAX_STEP_SECONDS = 0.1f
    }
}

@Composable
fun SmallToggle(
    label: String,
    active: Boolean,
    activeColor: Color,
    scale: Float = 1f,
    touchPadding: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val s = LocalControlScale.current * scale
    Box(
        // Touch area wider than box
        modifier = Modifier
            .clickable(interactionSource = null, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(touchPadding)
            .size(Dimens.Control * s)
            .clip(RoundedCornerShape(Dimens.RadiusSmall))
            .background(if (active) activeColor else Palette.Well),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Palette.Bg else Palette.TextSecondary,
            fontSize = buttonLabelSize() * min(1f, scale * LABEL_BOOST),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun TrackEditDialog(vm: EditorViewModel, track: Track, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(track.name) }
    var color by remember { mutableStateOf(track.color) }
    ModalDialog(
        title = "Edit track",
        onDismiss = onDismiss,
        actions = {
            FlatButton("Hapus track", Icons.Rounded.Delete, { vm.removeTrack(track.id); onDismiss() }, variant = ButtonVariant.DANGER)
            FlatButton("Simpan", Icons.Rounded.Check, {
                vm.updateTrack(track.id) { it.copy(name = name.trim().ifEmpty { track.name }.take(40), color = color) }
                onDismiss()
            }, variant = ButtonVariant.ACCENT)
        },
    ) {
        Text("Nama", style = TextStyles.Caption)
        TextInput(name, { name = it.take(40) }, Modifier.fillMaxWidth())
        Text("Warna", style = TextStyles.Caption)
        ColorSwatches(Defaults.TRACK_COLORS, color) { color = it }
        Text("Menghapus track juga menghapus semua klip di dalamnya.", style = TextStyles.Caption)
    }
}
