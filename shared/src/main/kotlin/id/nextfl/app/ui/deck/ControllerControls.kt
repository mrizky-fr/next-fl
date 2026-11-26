package id.nextfl.app.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.KnobScale
import id.nextfl.app.ui.components.rememberFrameTick
import id.nextfl.app.ui.components.toNormalized
import id.nextfl.app.ui.components.fromNormalized
import id.nextfl.app.ui.components.verticalValueDrag
import id.nextfl.app.ui.theme.Inter
import id.nextfl.app.ui.theme.Palette
import id.nextfl.core.util.AudioMath
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

private const val KNOB_DRAG_DP = 160f
private const val KNOB_SWEEP = 270f
private const val KNOB_START = -135f
private const val ARC_ROOM = 1.32f

// Full turn: 8% tempo
private const val RATE_PER_DEGREE = 0.08 / 360
private const val PLATTER_DEGREES_PER_SECOND = 200.0
private const val DEAD_ZONE = 0.15f
private const val METER_FLOOR_DB = -48f

/** Printed panel label text. */
fun printStyle(size: TextUnit, color: Color = ArtColors.PrintDim) =
    TextStyle(fontFamily = Inter, fontSize = size, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = color)

/** Hardware knob with printed label. */
@Composable
fun HardKnob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
    size: Dp,
    modifier: Modifier = Modifier,
    bipolar: Boolean = false,
    enabled: Boolean = true,
    showValue: Boolean = true,
) {
    val position = toNormalized(value, range, KnobScale.LINEAR)
    val currentPosition by rememberUpdatedState(position)
    val emit by rememberUpdatedState { p: Float -> onChange(fromNormalized(p, range, KnobScale.LINEAR)) }
    val reset by rememberUpdatedState { onChange(default) }
    val text = (size.value * 0.22f).coerceIn(7f, 11f).sp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = printStyle(text), maxLines = 1, softWrap = false)
        Canvas(
            Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    stateDescription = format(value)
                }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) { verticalValueDrag(KNOB_DRAG_DP * density, { currentPosition }, { emit(it) }, { reset() }) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val radius = this.size.minDimension / 2 / ARC_ROOM
            val origin = if (bipolar) KNOB_START + toNormalized(0f, range, KnobScale.LINEAR) * KNOB_SWEEP else KNOB_START
            drawKnob(center, radius, KNOB_START + position * KNOB_SWEEP, if (enabled) origin else null)
        }
        if (showValue) Text(format(value), style = printStyle(text, if (enabled) ArtColors.Print else ArtColors.PrintDim), maxLines = 1, softWrap = false)
    }
}

/** Vertical fader, 0 = bottom. */
@Composable
fun HardFader(
    position: Float,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    centerDetent: Boolean = false,
    enabled: Boolean = true,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
) {
    val current by rememberUpdatedState(position)
    val change by rememberUpdatedState(onChange)
    val reset by rememberUpdatedState(onReset)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        val capHeight = size.width * 0.42f
                        verticalValueDrag((size.height - capHeight).coerceAtLeast(1f), { current }, { change(it) }, { reset() }, onStart, onEnd)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val capHeight = size.width * 0.42f
        val track = Rect(size.width * 0.1f, capHeight / 2, size.width * 0.9f, size.height - capHeight / 2)
        drawFaderSlot(track, centerDetent = centerDetent)
        val y = track.bottom - position.coerceIn(0f, 1f) * track.height
        drawFaderCap(Offset(size.width / 2, y), size.width * 0.86f, capHeight, if (enabled) Color.White else ArtColors.PrintDim)
    }
}

/** Hold-to-play pad. */
@Composable
fun HoldPad(label: String, color: Color, active: Boolean, onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier
            .semantics { contentDescription = "Gate $label" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    press()
                    try {
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                        } while (change.pressed)
                    } finally {
                        release()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.06f
            drawPad(Rect(inset, inset, size.width - inset, size.height - inset), color, if (active) 1f else 0f)
        }
        BoxLabel(label, if (active) Color.White else ArtColors.Print)
    }
}

/** Lit rubber button, tap action. */
@Composable
fun LedButton(label: String, lit: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = ArtColors.Led, enabled: Boolean = true) {
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .semantics { contentDescription = label }
            .then(if (enabled) Modifier.pointerInput(Unit) { detectTapGestures { click() } } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.08f
            drawSmallButton(Rect(inset, inset, size.width - inset, size.height - inset), color, lit && enabled)
        }
        BoxLabel(label, if (!enabled) ArtColors.PrintDim else if (lit) Color.White else ArtColors.Print)
    }
}

/** Big round PLAY/CUE button. */
@Composable
fun RoundDeckButton(label: String, color: Color, lit: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, playGlyph: Boolean = false) {
    val click by rememberUpdatedState(onClick)
    Box(
        modifier
            .semantics { contentDescription = label }
            .pointerInput(Unit) { detectTapGestures { click() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 * 0.80f
            drawRoundButton(center, radius, color, lit)
            if (playGlyph) drawPlayPauseGlyph(center, radius * 0.62f, if (lit) color else ArtColors.Print)
        }
        if (!playGlyph) BoxLabel(label, if (lit) color else ArtColors.Print)
    }
}

// Label sized to its box
@Composable
private fun BoxLabel(label: String, color: Color) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fit = minOf(maxHeight.value * 0.34f, maxWidth.value / (label.length.coerceAtLeast(3) * 0.62f)).coerceIn(6f, 12f)
        Text(label, style = printStyle(fit.sp, color).copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

/** Live LED level meter. */
@Composable
fun LevelMeter(level: () -> Float, modifier: Modifier = Modifier) {
    val tick by rememberFrameTick()
    Canvas(modifier) {
        val peak = if (tick >= 0) level() else 0f
        val db = AudioMath.gainToDb(peak)
        drawMeter(Rect(0f, 0f, size.width, size.height), ((db - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f))
    }
}

/** Jog wheel: drag bends tempo. */
@Composable
fun DeckJog(vm: EditorViewModel, info: DeckInfo, playing: Boolean, modifier: Modifier = Modifier) {
    val currentInfo by rememberUpdatedState(info)
    val spin = remember { mutableFloatStateOf(0f) }
    var touched by remember { mutableStateOf(false) }
    val tick by rememberFrameTick(playing)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Jog deck ${info.number}" }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentInfo.clip?.let { DeckActions.resetRate(vm, it) } })
                }
                .pointerInput(Unit) {
                    var base = 1.0
                    var turned = 0.0
                    var dragging = false
                    try {
                        detectDragGestures(
                            onDragStart = {
                                base = currentInfo.rate
                                turned = 0.0
                                dragging = true
                                touched = true
                                vm.beginGesture()
                            },
                            onDragEnd = {
                                dragging = false
                                touched = false
                                vm.endGesture()
                            },
                            onDragCancel = {
                                dragging = false
                                touched = false
                                vm.endGesture()
                            },
                        ) { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val reach = hypot(change.position.x - center.x, change.position.y - center.y)
                            if (reach < size.width * DEAD_ZONE) return@detectDragGestures
                            val delta = wrapDegrees(angleOf(change.position, center) - angleOf(change.previousPosition, center))
                            turned += delta
                            spin.floatValue += delta.toFloat()
                            change.consume()
                            currentInfo.clip?.let { DeckActions.setRate(vm, it, base + turned * RATE_PER_DEGREE) }
                        }
                    } finally {
                        if (dragging) vm.endGesture()
                    }
                },
        ) {
            // Tick read redraws while playing
            val live = currentInfo.isLive && playing
            val seconds = if (tick >= 0 && live) vm.position() else currentInfo.playhead
            val angle = (seconds * PLATTER_DEGREES_PER_SECOND % 360).toFloat() + spin.floatValue
            val span = currentInfo.span
            val progress = if (span != null && span.duration > 0) ((seconds - span.start) / span.duration).toFloat() else 0f
            drawJogWheel(center, size.minDimension / 2, angle, progress, touched)
        }
        JogReadout(info)
    }
}

@Composable
private fun JogReadout(info: DeckInfo) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val unit = minOf(maxWidth, maxHeight).value
        val big = (unit * 0.085f).coerceIn(9f, 22f).sp
        val small = (unit * 0.045f).coerceIn(6f, 11f).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bpm = info.bpm
            Text(
                bpm?.let { String.format(Locale.ROOT, "%.1f", it) } ?: String.format(Locale.ROOT, "×%.2f", info.rate),
                style = printStyle(big, Color.White).copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            Text(if (bpm != null) "BPM" else "SPEED", style = printStyle(small, Palette.AccentLight), maxLines = 1)
            Text(
                String.format(Locale.ROOT, "%+.1f%%", (info.rate - 1) * 100),
                style = printStyle(small, ArtColors.Print).copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun angleOf(point: Offset, center: Offset) = Math.toDegrees(atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble()))

private fun wrapDegrees(value: Double): Double = ((value + 540) % 360) - 180
