package id.nextfl.app.ui.deck

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Hardware palette, light from top-left. */
object ArtColors {
    val BodyTop = Color(0xFF2A2C30)
    val BodyBottom = Color(0xFF15161A)
    val Plate = Color(0xFF1C1E22)
    val PlateDeep = Color(0xFF111215)
    val Rubber = Color(0xFF1A1B1E)
    val Metal = Color(0xFFC9CDD3)
    val MetalDark = Color(0xFF6B7078)
    val Glass = Color(0xFF05070A)
    val Print = Color(0xFFD4D7DC)
    val PrintDim = Color(0xFF8A8F97)
    val Led = Color(0xFF3C8DDB)
    val LedDeep = Color(0xFF0465C4)
    val Play = Color(0xFF2BD86A)
    val Cue = Color(0xFFFF9A1F)
    val Shadow = Color.Black
}

// Soft drop shadow, stacked rects
fun DrawScope.drawSoftShadow(rect: Rect, corner: Float, spread: Float, alpha: Float = 0.5f, dy: Float = spread * 0.35f) {
    val steps = 6
    for (i in steps downTo 1) {
        val grow = spread * i / steps
        drawRoundRect(
            color = ArtColors.Shadow.copy(alpha = alpha / steps * 1.6f * (1f - i / (steps + 1f))),
            topLeft = Offset(rect.left - grow, rect.top - grow + dy),
            size = Size(rect.width + grow * 2, rect.height + grow * 2),
            cornerRadius = CornerRadius(corner + grow),
        )
    }
}

/** Main body with bevelled edge. */
fun DrawScope.drawChassis(rect: Rect, corner: Float) {
    drawSoftShadow(rect, corner, corner * 1.4f, 0.7f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF34373C), ArtColors.BodyTop, ArtColors.BodyBottom), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
    )
    val edge = corner * 0.12f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.5f)),
            rect.top, rect.bottom,
        ),
        topLeft = Offset(rect.left + edge / 2, rect.top + edge / 2),
        size = Size(rect.width - edge, rect.height - edge),
        cornerRadius = CornerRadius(corner - edge / 2),
        style = Stroke(edge),
    )
}

/** Recessed panel, lit bottom edge. */
fun DrawScope.drawPlate(rect: Rect, corner: Float) {
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(ArtColors.PlateDeep, ArtColors.Plate), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
    )
    val line = (min(rect.width, rect.height) * 0.006f).coerceIn(1f, 3f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent, Color.White.copy(alpha = 0.10f)),
            rect.top, rect.bottom,
        ),
        topLeft = Offset(rect.left + line / 2, rect.top + line / 2),
        size = Size(rect.width - line, rect.height - line),
        cornerRadius = CornerRadius(corner),
        style = Stroke(line),
    )
}

/** Jog wheel, LED progress ring. */
fun DrawScope.drawJogWheel(center: Offset, radius: Float, angle: Float, progress: Float, touched: Boolean, accent: Color = ArtColors.Led) {
    // Shadow on the deck plate
    drawCircle(
        brush = Brush.radialGradient(
            0.80f to Color.Black.copy(alpha = 0.75f),
            1f to Color.Transparent,
            center = center + Offset(radius * 0.03f, radius * 0.07f),
            radius = radius * 1.12f,
        ),
        radius = radius * 1.12f,
        center = center + Offset(radius * 0.03f, radius * 0.07f),
    )
    // Outer plastic housing
    drawCircle(
        brush = Brush.radialGradient(
            0.86f to Color(0xFF0C0D0F),
            0.95f to Color(0xFF2B2E33),
            1f to Color(0xFF111215),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // Brushed metal side ring
    val ringOuter = radius * 0.955f
    drawCircle(brush = metalSweep(center), radius = ringOuter, center = center)
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = ringOuter, center = center, style = Stroke(radius * 0.012f))
    // Glossy black platter
    val platter = radius * 0.875f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF2A2D32),
            0.55f to Color(0xFF16181B),
            1f to Color(0xFF07080A),
            center = center + Offset(-radius * 0.18f, -radius * 0.22f),
            radius = platter * 1.25f,
        ),
        radius = platter,
        center = center,
    )
    // Fine grooves, fixed lighting
    val grooves = 22
    for (i in 0 until grooves) {
        val r = platter * (0.50f + 0.48f * i / grooves)
        drawCircle(Color.White.copy(alpha = if (i % 2 == 0) 0.035f else 0.018f), radius = r, center = center, style = Stroke(1f))
    }
    // Anisotropic sheen bands
    drawCircle(
        brush = Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.08f to Color.White.copy(alpha = 0.16f),
            0.16f to Color.Transparent,
            0.50f to Color.Transparent,
            0.58f to Color.White.copy(alpha = 0.09f),
            0.66f to Color.Transparent,
            1.00f to Color.Transparent,
            center = center,
        ),
        radius = platter * 0.98f,
        center = center,
    )
    // Rotating position mark
    rotate(angle, center) {
        val markOuter = platter * 0.95f
        val markInner = platter * 0.70f
        drawLine(
            color = if (touched) accent else Color.White.copy(alpha = 0.85f),
            start = Offset(center.x, center.y - markOuter),
            end = Offset(center.x, center.y - markInner),
            strokeWidth = radius * 0.028f,
            cap = StrokeCap.Round,
        )
    }
    // Platter rim highlight
    drawCircle(
        brush = Brush.sweepGradient(
            0.0f to Color.White.copy(alpha = 0.05f),
            0.62f to Color.White.copy(alpha = 0.32f),
            0.70f to Color.White.copy(alpha = 0.05f),
            1.0f to Color.White.copy(alpha = 0.05f),
            center = center,
        ),
        radius = platter,
        center = center,
        style = Stroke(radius * 0.012f),
    )
    drawJogDisplay(center, radius * 0.43f, angle, progress, touched, accent)
}

// Center LCD with LED ring
private fun DrawScope.drawJogDisplay(center: Offset, radius: Float, angle: Float, progress: Float, touched: Boolean, accent: Color) {
    drawCircle(Color.Black.copy(alpha = 0.6f), radius = radius * 1.08f, center = center + Offset(0f, radius * 0.06f))
    drawCircle(brush = metalSweep(center), radius = radius * 1.04f, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF0E141C),
            1f to ArtColors.Glass,
            center = center,
            radius = radius,
        ),
        radius = radius * 0.94f,
        center = center,
    )
    val ring = radius * 0.80f
    val ringStroke = radius * 0.09f
    val box = Size(ring * 2, ring * 2)
    val topLeft = Offset(center.x - ring, center.y - ring)
    val segments = 36
    val gap = 2.6f
    val sweep = 360f / segments
    for (i in 0 until segments) {
        val lit = i < (progress.coerceIn(0f, 1f) * segments).toInt()
        drawArc(
            color = if (lit) accent else Color.White.copy(alpha = 0.07f),
            startAngle = -90f + i * sweep + gap / 2,
            sweepAngle = sweep - gap,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(ringStroke),
        )
    }
    // Spinning dot on ring
    val radians = Math.toRadians((angle - 90f).toDouble())
    val dot = Offset(center.x + ring * cos(radians).toFloat(), center.y + ring * sin(radians).toFloat())
    drawCircle(Color.White, radius = ringStroke * 0.75f, center = dot)
    if (touched) drawCircle(accent.copy(alpha = 0.35f), radius = radius * 0.94f, center = center, style = Stroke(radius * 0.05f))
    // Glass reflection
    drawArc(
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center.y - radius, center.y),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.86f),
        size = Size(radius * 1.72f, radius * 1.72f),
        style = Stroke(radius * 0.06f, cap = StrokeCap.Round),
    )
}

private fun metalSweep(center: Offset): Brush = Brush.sweepGradient(
    0.00f to ArtColors.MetalDark,
    0.12f to ArtColors.Metal,
    0.25f to Color(0xFF8D929A),
    0.38f to Color(0xFF5A5E65),
    0.55f to ArtColors.Metal,
    0.62f to Color(0xFFEDEFF2),
    0.75f to Color(0xFF7D828A),
    0.88f to Color(0xFF50545A),
    1.00f to ArtColors.MetalDark,
    center = center,
)

/** Rotary knob, 0 is up. */
fun DrawScope.drawKnob(center: Offset, radius: Float, angle: Float, arcStart: Float? = null, accent: Color = ArtColors.Led, capColor: Color = Color(0xFF26282C)) {
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = radius * 1.02f, center = center + Offset(radius * 0.06f, radius * 0.14f))
    if (arcStart != null) {
        val arc = radius * 1.28f
        val box = Size(arc * 2, arc * 2)
        val topLeft = Offset(center.x - arc, center.y - arc)
        drawArc(Color.Black.copy(alpha = 0.6f), 135f, 270f, false, topLeft, box, style = Stroke(radius * 0.16f, cap = StrokeCap.Round))
        val from = arcStart - 90f
        val to = angle - 90f
        if (kotlin.math.abs(to - from) > 0.5f) {
            drawArc(accent, min(from, to), kotlin.math.abs(to - from), false, topLeft, box, style = Stroke(radius * 0.12f, cap = StrokeCap.Round))
        }
    }
    // Knurled skirt
    drawCircle(
        brush = Brush.radialGradient(0.7f to Color(0xFF3A3D42), 1f to Color(0xFF101113), center = center, radius = radius),
        radius = radius,
        center = center,
    )
    val ridges = 28
    for (i in 0 until ridges) {
        val a = Math.toRadians(i * 360.0 / ridges)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            Color.Black.copy(alpha = 0.45f),
            Offset(center.x + ca * radius * 0.86f, center.y + sa * radius * 0.86f),
            Offset(center.x + ca * radius * 0.99f, center.y + sa * radius * 0.99f),
            strokeWidth = radius * 0.05f,
        )
    }
    // Domed cap
    val cap = radius * 0.80f
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(capColor, Color.White, 0.30f),
            0.45f to capColor,
            1f to lerp(capColor, Color.Black, 0.55f),
            center = center + Offset(-cap * 0.35f, -cap * 0.45f),
            radius = cap * 1.7f,
        ),
        radius = cap,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.10f), radius = cap, center = center, style = Stroke(radius * 0.04f))
    rotate(angle, center) {
        drawLine(
            Color.White,
            Offset(center.x, center.y - cap * 0.92f),
            Offset(center.x, center.y - cap * 0.38f),
            strokeWidth = radius * 0.13f,
            cap = StrokeCap.Round,
        )
    }
}

/** Fader slot with scale ticks. */
fun DrawScope.drawFaderSlot(rect: Rect, ticks: Int = 11, centerDetent: Boolean = false) {
    val slotWidth = (rect.width * 0.14f).coerceAtLeast(2f)
    val slot = Rect(rect.center.x - slotWidth / 2, rect.top, rect.center.x + slotWidth / 2, rect.bottom)
    val tickLength = rect.width * 0.22f
    for (i in 0 until ticks) {
        val y = rect.top + rect.height * i / (ticks - 1)
        val major = i == 0 || i == ticks - 1 || (centerDetent && i == ticks / 2)
        val length = if (major) tickLength * 1.4f else tickLength
        val color = if (centerDetent && i == ticks / 2) ArtColors.Led else ArtColors.PrintDim.copy(alpha = 0.7f)
        drawLine(color, Offset(rect.left, y), Offset(rect.left + length, y), strokeWidth = 1.2f)
        drawLine(color, Offset(rect.right - length, y), Offset(rect.right, y), strokeWidth = 1.2f)
    }
    drawRoundRect(Color.Black, slot.topLeft, slot.size, CornerRadius(slotWidth / 2))
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color.Black, Color(0xFF2A2C30)), slot.left, slot.right),
        topLeft = slot.topLeft,
        size = slot.size,
        cornerRadius = CornerRadius(slotWidth / 2),
        style = Stroke(1f),
    )
}

/** Fader cap at [center]. */
fun DrawScope.drawFaderCap(center: Offset, width: Float, height: Float, lineColor: Color = Color.White) {
    val rect = Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)
    val corner = CornerRadius(min(width, height) * 0.14f)
    drawRoundRect(Color.Black.copy(alpha = 0.6f), rect.topLeft + Offset(width * 0.04f, height * 0.22f), rect.size, corner)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF4A4E55), Color(0xFF26282C), Color(0xFF15171A)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = corner,
    )
    val grooves = 4
    for (i in 1..grooves) {
        val y = rect.top + rect.height * i / (grooves + 1)
        drawLine(Color.Black.copy(alpha = 0.55f), Offset(rect.left + width * 0.1f, y), Offset(rect.right - width * 0.1f, y), strokeWidth = height * 0.05f)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(rect.left + width * 0.1f, y + height * 0.05f), Offset(rect.right - width * 0.1f, y + height * 0.05f), strokeWidth = 1f)
    }
    drawLine(lineColor, Offset(rect.left + width * 0.06f, center.y), Offset(rect.right - width * 0.06f, center.y), strokeWidth = height * 0.08f)
    drawRoundRect(Color.White.copy(alpha = 0.18f), rect.topLeft, rect.size, corner, style = Stroke(1f))
}

/** Rubber performance pad, LED backlit. */
fun DrawScope.drawPad(rect: Rect, color: Color, lit: Float) {
    val corner = CornerRadius(min(rect.width, rect.height) * 0.12f)
    val glow = lit.coerceIn(0f, 1f)
    if (glow > 0f) {
        val spread = min(rect.width, rect.height) * 0.35f
        drawRoundRect(
            color = color.copy(alpha = 0.35f * glow),
            topLeft = Offset(rect.left - spread / 3, rect.top - spread / 3),
            size = Size(rect.width + spread * 2 / 3, rect.height + spread * 2 / 3),
            cornerRadius = CornerRadius(corner.x + spread / 3),
        )
    }
    drawRoundRect(Color.Black.copy(alpha = 0.6f), rect.topLeft + Offset(0f, rect.height * 0.07f), rect.size, corner)
    val base = lerp(Color(0xFF26282C), color, 0.18f + 0.72f * glow)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f), base, lerp(base, Color.Black, 0.35f)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = corner,
    )
    val inset = min(rect.width, rect.height) * 0.10f
    drawRoundRect(
        color = lerp(color, Color.White, 0.25f).copy(alpha = 0.25f + 0.55f * glow),
        topLeft = Offset(rect.left + inset, rect.top + inset),
        size = Size(rect.width - inset * 2, rect.height - inset * 2),
        cornerRadius = CornerRadius(corner.x * 0.6f),
        style = Stroke((inset * 0.35f).coerceAtLeast(1f)),
    )
}

/** PLAY/CUE button, LED ring. */
fun DrawScope.drawRoundButton(center: Offset, radius: Float, color: Color, lit: Boolean) {
    drawCircle(Color.Black.copy(alpha = 0.6f), radius = radius * 1.04f, center = center + Offset(radius * 0.05f, radius * 0.12f))
    drawCircle(
        brush = Brush.radialGradient(
            0.75f to color.copy(alpha = if (lit) 1f else 0.28f),
            1f to color.copy(alpha = if (lit) 0.35f else 0.08f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    if (lit) drawCircle(color.copy(alpha = 0.25f), radius = radius * 1.25f, center = center, style = Stroke(radius * 0.18f))
    val cap = radius * 0.80f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF3E4147),
            0.6f to Color(0xFF1D1F23),
            1f to Color(0xFF0B0C0E),
            center = center + Offset(-cap * 0.3f, -cap * 0.4f),
            radius = cap * 1.6f,
        ),
        radius = cap,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.10f), radius = cap, center = center, style = Stroke(radius * 0.04f))
}

/** Small rectangular rubber button. */
fun DrawScope.drawSmallButton(rect: Rect, color: Color, lit: Boolean) {
    val corner = CornerRadius(min(rect.width, rect.height) * 0.18f)
    drawRoundRect(Color.Black.copy(alpha = 0.6f), rect.topLeft + Offset(0f, rect.height * 0.10f), rect.size, corner)
    val base = if (lit) lerp(Color(0xFF26282C), color, 0.75f) else Color(0xFF26282C)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.16f), base, lerp(base, Color.Black, 0.4f)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = corner,
    )
    if (lit) {
        drawRoundRect(color.copy(alpha = 0.35f), rect.topLeft - Offset(2f, 2f), Size(rect.width + 4f, rect.height + 4f), CornerRadius(corner.x + 2f), style = Stroke(2f))
    }
}

/** Bezel around [screenInner]. */
fun DrawScope.drawScreenBezel(rect: Rect, corner: Float) {
    drawSoftShadow(rect, corner, corner * 1.2f, 0.6f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF3A3D42), Color(0xFF1A1C1F), Color(0xFF0E0F11)), rect.top, rect.bottom),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
    )
    val inner = screenInner(rect)
    drawRect(Color.Black, inner.topLeft - Offset(2f, 2f), Size(inner.width + 4f, inner.height + 4f))
}

fun screenInner(rect: Rect): Rect {
    val pad = min(rect.width, rect.height) * 0.035f
    return Rect(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad)
}

/** Glass reflection over screen content. */
fun DrawScope.drawScreenGlass(inner: Rect) {
    val path = Path().apply {
        moveTo(inner.left, inner.top)
        lineTo(inner.left + inner.width * 0.55f, inner.top)
        lineTo(inner.left + inner.width * 0.30f, inner.bottom)
        lineTo(inner.left, inner.bottom)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), inner.topLeft, inner.bottomCenter))
}

/** Segmented LED meter. */
fun DrawScope.drawMeter(rect: Rect, level: Float, segments: Int = 12) {
    val gap = rect.height * 0.25f / segments
    val segment = (rect.height - gap * (segments - 1)) / segments
    val lit = (level.coerceIn(0f, 1f) * segments).toInt()
    for (i in 0 until segments) {
        val top = rect.bottom - (i + 1) * segment - i * gap
        val hot = when {
            i >= segments - 1 -> Color(0xFFFF4D4D)
            i >= segments - 3 -> ArtColors.Cue
            else -> ArtColors.Play
        }
        drawRect(if (i < lit) hot else hot.copy(alpha = 0.12f), Offset(rect.left, top), Size(rect.width, segment))
    }
}

/** Play/pause glyph for PLAY button. */
fun DrawScope.drawPlayPauseGlyph(center: Offset, size: Float, color: Color) {
    val h = size * 0.5f
    val path = Path().apply {
        moveTo(center.x - size * 0.55f, center.y - h)
        lineTo(center.x - size * 0.55f, center.y + h)
        lineTo(center.x + size * 0.05f, center.y)
        close()
    }
    drawPath(path, color)
    val bar = size * 0.13f
    drawRect(color, Offset(center.x + size * 0.18f, center.y - h), Size(bar, h * 2))
    drawRect(color, Offset(center.x + size * 0.42f, center.y - h), Size(bar, h * 2))
}
