package id.nextfl.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.nextfl.core.util.AudioMath
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

enum class KnobScale { LINEAR, LOG }

private const val START_ANGLE = 135f
private const val SWEEP = 270f
private const val DOUBLE_TAP_MS = 300L
private const val KNOB_DRAG_DP = 160f
private const val CLIP_RATIO = 0.94f

fun toNormalized(value: Float, range: ClosedFloatingPointRange<Float>, scale: KnobScale): Float {
    val position = when (scale) {
        KnobScale.LOG -> ln(value / range.start) / ln(range.endInclusive / range.start)
        KnobScale.LINEAR -> (value - range.start) / (range.endInclusive - range.start)
    }
    return position.coerceIn(0f, 1f)
}

fun fromNormalized(position: Float, range: ClosedFloatingPointRange<Float>, scale: KnobScale): Float {
    val p = position.coerceIn(0f, 1f)
    return when (scale) {
        KnobScale.LOG -> range.start * (range.endInclusive / range.start).pow(p)
        KnobScale.LINEAR -> range.start + p * (range.endInclusive - range.start)
    }
}

/** Vertical drag, double-tap reset. */
suspend fun PointerInputScope.verticalValueDrag(
    pixelsForFullRange: Float,
    current: () -> Float,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
) {
    var lastTap = 0L
    awaitEachGesture {
        val down = awaitFirstDown()
        if (down.uptimeMillis - lastTap < DOUBLE_TAP_MS) {
            lastTap = 0L
            onReset()
            return@awaitEachGesture
        }
        lastTap = down.uptimeMillis
        val startY = down.position.y
        val startValue = current()
        onStart()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            onChange((startValue + (startY - change.position.y) / pixelsForFullRange).coerceIn(0f, 1f))
            change.consume()
        }
        onEnd()
    }
}

@Composable
fun rememberFrameTick(active: Boolean = true): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        while (active) withFrameNanos { tick.longValue = it }
    }
    return tick
}

private const val DISABLED_ALPHA = 0.4f

@Composable
fun Knob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    scale: KnobScale = KnobScale.LINEAR,
    bipolar: Boolean = false,
    size: Dp = 42.dp,
    enabled: Boolean = true,
) {
    val position = toNormalized(value, range, scale)
    val currentPosition by rememberUpdatedState(position)
    val emit by rememberUpdatedState { p: Float -> onChange(fromNormalized(p, range, scale)) }
    val reset by rememberUpdatedState { onChange(default) }
    Column(
        // Grows to fit long labels
        modifier = modifier.widthIn(min = 58.dp).alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = TextStyles.Label, maxLines = 1, softWrap = false)
        Canvas(
            modifier = Modifier
                .size(size)
                .semantics {
                    contentDescription = label
                    stateDescription = format(value)
                }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            verticalValueDrag(KNOB_DRAG_DP * density, { currentPosition }, { emit(it) }, { reset() })
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val stroke = 3.dp.toPx()
            val arcSize = Size(this.size.width - stroke * 2, this.size.height - stroke * 2)
            val topLeft = Offset(stroke, stroke)
            drawCircle(Palette.Control, radius = this.size.minDimension / 2 - stroke * 1.6f)
            drawArc(Palette.Well, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke))
            val origin = if (bipolar) START_ANGLE + toNormalized(0f, range, scale) * SWEEP else START_ANGLE
            val angle = START_ANGLE + position * SWEEP
            if (abs(angle - origin) > 0.5f) {
                drawArc(Palette.Accent, minOf(origin, angle), abs(angle - origin), false, topLeft, arcSize, style = Stroke(stroke))
            }
            val radians = Math.toRadians(angle.toDouble())
            val tipRadius = this.size.minDimension / 2 - stroke * 3.2f
            drawLine(
                Palette.White,
                center,
                Offset(center.x + (tipRadius * cos(radians)).toFloat(), center.y + (tipRadius * sin(radians)).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(format(value), style = TextStyles.LcdSmall, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun Fader(db: Float, onChange: (Float) -> Unit, label: String, height: Dp, onStart: () -> Unit = {}, onEnd: () -> Unit = {}) {
    val position = AudioMath.dbToFader(db)
    val currentPosition by rememberUpdatedState(position)
    val emit by rememberUpdatedState { p: Float -> onChange(Math.round(AudioMath.faderToDb(p) * 10) / 10f) }
    val reset by rememberUpdatedState { onChange(0f) }
    Canvas(
        modifier = Modifier
            .width(30.dp)
            .height(height)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                verticalValueDrag(height.toPx(), { currentPosition }, { emit(it) }, { reset() }, onStart, onEnd)
            },
    ) {
        val thumbHeight = 20.dp.toPx()
        val travel = size.height - thumbHeight
        val groove = 4.dp.toPx()
        drawRect(Palette.Well, Offset(center.x - groove / 2, thumbHeight / 2), Size(groove, travel))
        val unityY = travel * (1 - AudioMath.dbToFader(0f)) + thumbHeight / 2
        drawLine(Palette.TextMuted, Offset(2.dp.toPx(), unityY), Offset(size.width - 2.dp.toPx(), unityY), strokeWidth = 1.dp.toPx())
        val top = travel * (1 - position)
        drawRoundRect(Palette.ControlHigh, Offset(1.dp.toPx(), top), Size(size.width - 2.dp.toPx(), thumbHeight), CornerRadius(2.dp.toPx()))
        drawLine(
            Palette.White,
            Offset(center.x - 8.dp.toPx(), top + thumbHeight / 2),
            Offset(center.x + 8.dp.toPx(), top + thumbHeight / 2),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

/** Peak meter with hold. */
@Composable
fun LevelMeter(peak: () -> Float, height: Dp, modifier: Modifier = Modifier, width: Dp = 6.dp) {
    val tick by rememberFrameTick()
    val state = remember { MeterState() }
    Canvas(modifier.width(width).height(height)) {
        state.update(peak(), tick)
        drawRect(Palette.Well)
        val clipping = state.holdRatio > CLIP_RATIO
        val levelTop = size.height * (1 - state.levelRatio)
        drawRect(if (clipping) Palette.Danger else Palette.Accent, Offset(0f, levelTop), Size(size.width, size.height - levelTop))
        val holdY = size.height * (1 - state.holdRatio)
        drawRect(if (clipping) Palette.Danger else Palette.White, Offset(0f, holdY), Size(size.width, 2.dp.toPx()))
    }
}

private class MeterState {
    private var level = FLOOR
    private var hold = FLOOR
    private var holdTime = 0f
    private var lastNanos = 0L
    val levelRatio get() = ratio(level)
    val holdRatio get() = ratio(hold)

    fun update(peak: Float, nanos: Long) {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        val db = if (peak > 0f) 20f * log10(peak) else FLOOR
        level = maxOf(db, level - FALL_DB_PER_SECOND * dt)
        holdTime = if (db >= hold) 0f else holdTime + dt
        if (db >= hold || holdTime > HOLD_SECONDS) hold = level
    }

    private fun ratio(db: Float) = (db.coerceIn(FLOOR, CEIL) - FLOOR) / (CEIL - FLOOR)

    companion object {
        const val FLOOR = -48f
        const val CEIL = 3f
        const val FALL_DB_PER_SECOND = 30f
        const val HOLD_SECONDS = 0.8f
    }
}

@Composable
fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
    origin: Float = range.start,
    enabled: Boolean = true,
) {
    val currentRange by rememberUpdatedState(range)
    val emit by rememberUpdatedState(onChange)
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = TextStyles.Body, modifier = Modifier.weight(1f))
            Text(format(value), style = TextStyles.LcdSmall)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun apply(x: Float) {
                            val p = (x / size.width).coerceIn(0f, 1f)
                            emit(currentRange.start + p * (currentRange.endInclusive - currentRange.start))
                        }
                        apply(down.position.x)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            apply(change.position.x)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                fun norm(v: Float) = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val p = norm(value)
                val from = norm(origin)
                val trackHeight = 4.dp.toPx()
                val y = center.y - trackHeight / 2
                drawRect(Palette.Well, Offset(0f, y), Size(size.width, trackHeight))
                // Fill grows from origin
                drawRect(Palette.Accent, Offset(size.width * minOf(from, p), y), Size(size.width * kotlin.math.abs(p - from), trackHeight))
                val thumbWidth = 8.dp.toPx()
                val thumbX = (size.width * p - thumbWidth / 2).coerceIn(0f, size.width - thumbWidth)
                drawRoundRect(Palette.White, Offset(thumbX, center.y - 8.dp.toPx()), Size(thumbWidth, 16.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

/** Vertical fader for graphic EQ. */
@Composable
fun VerticalSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    origin: Float = range.start,
    enabled: Boolean = true,
) {
    val currentRange by rememberUpdatedState(range)
    val emit by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun apply(y: Float) {
                        val p = 1f - (y / size.height).coerceIn(0f, 1f)
                        emit(currentRange.start + p * (currentRange.endInclusive - currentRange.start))
                    }
                    apply(down.position.y)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        apply(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        fun norm(v: Float) = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val p = norm(value)
        val from = norm(origin)
        val trackWidth = 4.dp.toPx()
        val x = center.x - trackWidth / 2
        drawRect(Palette.Well, Offset(x, 0f), Size(trackWidth, size.height))
        val top = size.height * (1 - maxOf(p, from))
        val bottom = size.height * (1 - minOf(p, from))
        drawRect(Palette.Accent, Offset(x, top), Size(trackWidth, bottom - top))
        val thumbWidth = 16.dp.toPx()
        val thumbHeight = 8.dp.toPx()
        val thumbY = (size.height * (1 - p) - thumbHeight / 2).coerceIn(0f, size.height - thumbHeight)
        drawRoundRect(Palette.White, Offset(center.x - thumbWidth / 2, thumbY), Size(thumbWidth, thumbHeight), CornerRadius(2.dp.toPx()))
    }
}

/** Horizontal peak meter, dB scale. */
@Composable
fun HorizontalMeter(peak: () -> Float, modifier: Modifier = Modifier) {
    val tick by rememberFrameTick()
    val state = remember { MeterState() }
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        state.update(peak(), tick)
        drawRect(Palette.Well)
        val clipping = state.holdRatio > CLIP_RATIO
        drawRect(if (clipping) Palette.Danger else Palette.Accent, Offset.Zero, Size(size.width * state.levelRatio, size.height))
        val holdX = (size.width * state.holdRatio).coerceAtMost(size.width - 2.dp.toPx())
        drawRect(if (clipping) Palette.Danger else Palette.White, Offset(holdX, 0f), Size(2.dp.toPx(), size.height))
    }
}

/** Meter position 0..1 for dB. */
fun meterRatio(db: Float): Float = (db.coerceIn(MeterState.FLOOR, MeterState.CEIL) - MeterState.FLOOR) / (MeterState.CEIL - MeterState.FLOOR)
