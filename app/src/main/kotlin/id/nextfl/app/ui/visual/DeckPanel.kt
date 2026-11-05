package id.nextfl.app.ui.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.Knob
import id.nextfl.app.ui.components.PANEL_GAP
import id.nextfl.app.ui.components.PanelHeader
import id.nextfl.app.ui.components.rememberFrameTick
import id.nextfl.app.ui.playlist.ClipLevelSection
import id.nextfl.app.ui.playlist.TempoSection
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.Clip
import id.nextfl.core.model.DjSettings
import id.nextfl.core.model.FxRanges
import id.nextfl.core.state.EditorState
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private val FX_ROW_HEIGHT = 84.dp
private val FX_ROW_WIDTH = 248.dp
private val MAX_WHEEL = 260.dp
private val DECK_GAP = 8.dp

// One full turn = 8% tempo
private const val RATE_PER_DEGREE = 0.08 / 360
private const val PLATTER_DEGREES_PER_SECOND = 200.0
private const val DEAD_ZONE = 0.15f
private const val OFF = 0.01f

/** Wheel and FX left, clip settings right. */
@Composable
fun DeckPanel(vm: EditorViewModel, state: EditorState, playing: Boolean, modifier: Modifier = Modifier) {
    val clip = deckClip(state)
    Row(modifier.padding(DECK_GAP), horizontalArrangement = Arrangement.spacedBy(DECK_GAP)) {
        BoxWithConstraints(Modifier.width(FX_ROW_WIDTH).fillMaxHeight()) {
            val wheel = min(min(maxWidth, maxHeight - FX_ROW_HEIGHT - DECK_GAP), MAX_WHEEL)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DECK_GAP)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (wheel > 40.dp) JogWheel(vm, state, clip, playing, Modifier.size(wheel))
                }
                DjFxRow(vm, state.doc.dj)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
        ) {
            if (clip == null) {
                Text("Belum ada klip. Ketuk \"+ Audio\" untuk mulai.", style = TextStyles.Caption)
            } else {
                PanelHeader { Text(clip.name, style = TextStyles.Title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val span = clipSpan(clip, state.assets, state.doc.bpm)
                TempoSection(vm, clip, state.assets[clip.assetId], span.rate, state.doc.bpm)
                ClipLevelSection(vm, clip, span.duration)
            }
        }
    }
}

// Selected, else first on deck track
private fun deckClip(state: EditorState): Clip? {
    val clips = state.doc.clips
    clips.find { it.id == state.selectedClipId }?.let { return it }
    val trackId = state.selectedTrackId ?: state.doc.tracks.firstOrNull()?.id
    return clips.filter { it.trackId == trackId }.minByOrNull { it.startBeat } ?: clips.minByOrNull { it.startBeat }
}

@Composable
private fun JogWheel(vm: EditorViewModel, state: EditorState, clip: Clip?, playing: Boolean, modifier: Modifier) {
    val rate = clip?.let { clipSpan(it, state.assets, state.doc.bpm).rate } ?: 1.0
    val assetBpm = clip?.let { state.assets[it.assetId]?.bpm }
    val currentRate by rememberUpdatedState(rate)
    val currentClip by rememberUpdatedState(clip)
    val spin = remember { mutableFloatStateOf(0f) }
    val tick by rememberFrameTick(playing)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentClip?.let { vm.setDeckRate(it.id, 1.0) } })
                }
                .pointerInput(Unit) {
                    var base = 1.0
                    var turned = 0.0
                    detectDragGestures(
                        onDragStart = {
                            base = currentRate
                            turned = 0.0
                            vm.beginGesture()
                        },
                        onDragEnd = { vm.endGesture() },
                        onDragCancel = { vm.endGesture() },
                    ) { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val reach = hypot(change.position.x - center.x, change.position.y - center.y)
                        if (reach < size.width * DEAD_ZONE) return@detectDragGestures
                        val delta = wrapDegrees(angleOf(change.position, center) - angleOf(change.previousPosition, center))
                        turned += delta
                        spin.floatValue += delta.toFloat()
                        change.consume()
                        currentClip?.let { vm.setDeckRate(it.id, base + turned * RATE_PER_DEGREE) }
                    }
                },
        ) {
            // Tick read redraws while playing
            val seconds = if (tick >= 0) vm.position() else 0.0
            val radius = size.minDimension / 2
            val angle = (seconds * PLATTER_DEGREES_PER_SECOND % 360).toFloat() + spin.floatValue
            drawCircle(Palette.Control, radius)
            drawCircle(PLATTER, radius * 0.92f)
            for (i in 1..GROOVES) {
                drawCircle(GROOVE, radius * (0.5f + i * 0.075f), style = Stroke(1.dp.toPx()))
            }
            rotate(angle) {
                drawLine(Palette.Accent, Offset(center.x, center.y - radius * 0.9f), Offset(center.x, center.y - radius * 0.5f), 4.dp.toPx(), StrokeCap.Round)
                drawCircle(Palette.White, 3.dp.toPx(), Offset(center.x, center.y + radius * 0.7f))
            }
            drawCircle(Palette.Accent, radius * LABEL_RADIUS)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val value = assetBpm?.let { String.format(Locale.ROOT, "%.1f", it * rate) } ?: String.format(Locale.ROOT, "×%.3f", rate)
            Text(value, color = Palette.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (assetBpm != null) "BPM" else "SPEED", style = TextStyles.Label, color = Palette.White)
            Text(String.format(Locale.ROOT, "%+.1f%%", (rate - 1) * 100), style = TextStyles.LcdSmall, color = Palette.White)
        }
    }
}

@Composable
private fun DjFxRow(vm: EditorViewModel, dj: DjSettings) {
    Row(Modifier.width(FX_ROW_WIDTH), horizontalArrangement = Arrangement.SpaceBetween) {
        Knob("Filter", dj.filter, FxRanges.BIPOLAR, 0f, { v -> vm.updateDj("filter") { it.copy(filter = v) } }, ::formatFilter, bipolar = true, size = KNOB)
        Knob("Echo", dj.echo, FxRanges.UNIT, 0f, { v -> vm.updateDj("echo") { it.copy(echo = v) } }, ::formatAmount, size = KNOB)
        Knob("Flanger", dj.flanger, FxRanges.UNIT, 0f, { v -> vm.updateDj("flanger") { it.copy(flanger = v) } }, ::formatAmount, size = KNOB)
        Knob("Crush", dj.crush, FxRanges.UNIT, 0f, { v -> vm.updateDj("crush") { it.copy(crush = v) } }, ::formatAmount, size = KNOB)
    }
}

private val KNOB = 38.dp
private val PLATTER = Color(0xFF0B0C0E)
private val GROOVE = Color(0x14FFFFFF)
private const val GROOVES = 5
private const val LABEL_RADIUS = 0.46f

private fun formatFilter(value: Float): String = when {
    value <= -OFF -> "LPF ${(-value * 100).roundToInt()}%"
    value >= OFF -> "HPF ${(value * 100).roundToInt()}%"
    else -> "Off"
}

private fun formatAmount(value: Float): String = if (value < OFF) "Off" else "${(value * 100).roundToInt()}%"

private fun angleOf(point: Offset, center: Offset) = Math.toDegrees(atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble()))

private fun wrapDegrees(value: Double): Double = ((value + 540) % 360) - 180
