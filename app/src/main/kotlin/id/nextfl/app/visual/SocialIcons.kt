package id.nextfl.app.visual

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withTranslation
import id.nextfl.core.model.SocialPlatform
import kotlin.math.ceil

/** Bitmaps cached per pixel size. */
class SocialIcons {
    private val cache = HashMap<SocialPlatform, Bitmap>()
    private var cachedSize = 0

    // No recycle: render thread may hold
    fun bitmap(platform: SocialPlatform, size: Int): Bitmap {
        if (size != cachedSize) {
            cache.clear()
            cachedSize = size
        }
        return cache.getOrPut(platform) { SocialIconPainter.render(platform, size) }
    }
}

/** Glossy extruded platform badges. */
object SocialIconPainter {
    private const val PAD = 0.3f
    private const val DEPTH = 0.075f
    private const val CORNER = 0.24f
    private const val FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG

    private val TIKTOK_CYAN = Color.rgb(0x25, 0xF4, 0xEE)
    private val TIKTOK_RED = Color.rgb(0xFE, 0x2C, 0x55)
    private val SPOTIFY_INK = Color.rgb(0x12, 0x12, 0x12)
    private val GLYPH_SHADOW = Color.argb(90, 0, 0, 0)
    private val INSTAGRAM_COLORS = intArrayOf(
        Color.rgb(0xFD, 0xF4, 0x97),
        Color.rgb(0xFD, 0xF4, 0x97),
        Color.rgb(0xFD, 0x59, 0x49),
        Color.rgb(0xD6, 0x24, 0x9F),
        Color.rgb(0x28, 0x5A, 0xEB),
    )
    private val INSTAGRAM_STOPS = floatArrayOf(0f, 0.05f, 0.45f, 0.6f, 0.9f)

    /** Face centered; padding holds shadow. */
    fun render(platform: SocialPlatform, size: Int): Bitmap {
        val pad = ceil(size * PAD).toInt()
        val bitmap = Bitmap.createBitmap(size + pad * 2, size + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(pad.toFloat(), pad.toFloat())
        val s = size.toFloat()
        when (platform) {
            SocialPlatform.TIKTOK -> tiktok(canvas, s)
            SocialPlatform.YOUTUBE -> youtube(canvas, s)
            SocialPlatform.SPOTIFY -> spotify(canvas, s)
            SocialPlatform.INSTAGRAM -> instagram(canvas, s)
        }
        return bitmap
    }

    private fun tiktok(canvas: Canvas, s: Float) {
        val shape = roundedTile(s)
        val face = linear(0f, s, Color.rgb(0x2A, 0x2A, 0x2A), Color.BLACK)
        val g = s * 0.6f
        val note = tiktokNote((s - g) / 2 + g * 0.02f, (s - g) / 2, g)
        val offset = g * 0.04f
        badge(canvas, s, shape, face, solid(Color.rgb(0x3A, 0x3A, 0x3A))) { paint, tint ->
            paint.color = tint(TIKTOK_CYAN)
            withTranslation(-offset, -offset) { drawPath(note, paint) }
            paint.color = tint(TIKTOK_RED)
            withTranslation(offset, offset) { drawPath(note, paint) }
            paint.color = tint(Color.WHITE)
            drawPath(note, paint)
        }
    }

    // Note: stem, ring bowl, hook
    private fun tiktokNote(left: Float, top: Float, g: Float): Path {
        fun x(v: Float) = left + v * g
        fun y(v: Float) = top + v * g
        val note = Path().apply { addRect(x(0.46f), y(0f), x(0.62f), y(0.70f), Path.Direction.CW) }
        val ring = Path().apply { addCircle(x(0.33f), y(0.70f), g * 0.29f, Path.Direction.CW) }
        ring.op(Path().apply { addCircle(x(0.33f), y(0.70f), g * 0.13f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        val hook = Path().apply {
            moveTo(x(0.62f), y(0f))
            cubicTo(x(0.64f), y(0.14f), x(0.76f), y(0.24f), x(0.92f), y(0.24f))
            lineTo(x(0.92f), y(0.41f))
            cubicTo(x(0.80f), y(0.41f), x(0.70f), y(0.37f), x(0.62f), y(0.31f))
            close()
        }
        note.op(ring, Path.Op.UNION)
        note.op(hook, Path.Op.UNION)
        return note
    }

    private fun youtube(canvas: Canvas, s: Float) {
        val top = s * 0.15f
        val bottom = s * 0.85f
        val shape = Path().apply { addRoundRect(RectF(0f, top, s, bottom), s * 0.22f, s * 0.22f, Path.Direction.CW) }
        val face = linear(top, bottom, Color.rgb(0xFF, 0x3B, 0x3B), Color.rgb(0xE0, 0x00, 0x00))
        val play = Path().apply {
            moveTo(s * 0.40f, s * 0.35f)
            lineTo(s * 0.67f, s * 0.5f)
            lineTo(s * 0.40f, s * 0.65f)
            close()
        }
        badge(canvas, s, shape, face, solid(Color.rgb(0x8E, 0x00, 0x00))) { paint, tint ->
            paint.pathEffect = CornerPathEffect(s * 0.03f)
            paint.color = tint(Color.WHITE)
            drawPath(play, paint)
            paint.pathEffect = null
        }
    }

    private fun spotify(canvas: Canvas, s: Float) {
        val r = s / 2
        val shape = Path().apply { addCircle(r, r, r, Path.Direction.CW) }
        val face = linear(0f, s, Color.rgb(0x2B, 0xE3, 0x6E), Color.rgb(0x17, 0xB3, 0x4F))
        // Start, control, end, width
        val arcs = listOf(
            floatArrayOf(-0.58f, -0.20f, 0f, -0.52f, 0.56f, -0.04f, 0.165f),
            floatArrayOf(-0.50f, 0.10f, 0f, -0.17f, 0.48f, 0.24f, 0.135f),
            floatArrayOf(-0.42f, 0.38f, 0f, 0.15f, 0.38f, 0.50f, 0.11f),
        ).map { a ->
            a[6] * r to Path().apply {
                moveTo(r + a[0] * r, r + a[1] * r)
                quadTo(r + a[2] * r, r + a[3] * r, r + a[4] * r, r + a[5] * r)
            }
        }
        badge(canvas, s, shape, face, solid(Color.rgb(0x0C, 0x6E, 0x30))) { paint, tint ->
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = tint(SPOTIFY_INK)
            arcs.forEach { (width, arc) ->
                paint.strokeWidth = width
                drawPath(arc, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }

    private fun instagram(canvas: Canvas, s: Float) {
        val shape = roundedTile(s)
        val face = RadialGradient(s * 0.3f, s * 1.07f, s * 1.28f, INSTAGRAM_COLORS, INSTAGRAM_STOPS, Shader.TileMode.CLAMP)
        val side = LinearGradient(0f, 0f, s, 0f, Color.rgb(0xB8, 0x8A, 0x1E), Color.rgb(0x8E, 0x16, 0x6E), Shader.TileMode.CLAMP)
        val body = RectF(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f)
        badge(canvas, s, shape, face, Paint(FLAGS).apply { shader = side }) { paint, tint ->
            paint.color = tint(Color.WHITE)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.075f
            drawRoundRect(body, s * 0.17f, s * 0.17f, paint)
            drawCircle(s * 0.5f, s * 0.5f, s * 0.135f, paint)
            paint.style = Paint.Style.FILL
            drawCircle(s * 0.655f, s * 0.345f, s * 0.042f, paint)
        }
    }

    // Shadow, extrusion, face, glyph, gloss, rim
    private fun badge(canvas: Canvas, s: Float, shape: Path, face: Shader, side: Paint, glyph: Canvas.(Paint, (Int) -> Int) -> Unit) {
        val depth = s * DEPTH
        val shadow = Paint(FLAGS).apply {
            color = Color.argb(150, 0, 0, 0)
            maskFilter = BlurMaskFilter(s * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.withTranslation(0f, depth + s * 0.05f) { drawPath(shape, shadow) }
        var offset = depth
        while (offset > 0f) {
            canvas.withTranslation(0f, offset) { drawPath(shape, side) }
            offset -= 1f
        }
        val layer = canvas.saveLayer(null, null)
        canvas.drawPath(shape, Paint(FLAGS).apply { shader = face })
        val ink = Paint(FLAGS)
        canvas.withTranslation(0f, s * 0.025f) { glyph(ink) { GLYPH_SHADOW } }
        canvas.glyph(ink) { it }
        drawGloss(canvas, s)
        drawRim(canvas, s, shape)
        canvas.restoreToCount(layer)
    }

    // Atop keeps light inside face
    private fun drawGloss(canvas: Canvas, s: Float) {
        val gloss = Paint(FLAGS).apply {
            shader = linear(0f, s * 0.46f, Color.argb(95, 255, 255, 255), Color.argb(10, 255, 255, 255))
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawOval(RectF(-s * 0.25f, -s * 0.62f, s * 1.25f, s * 0.46f), gloss)
    }

    private fun drawRim(canvas: Canvas, s: Float, shape: Path) {
        val rim = Paint(FLAGS).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.05f
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(70, 0, 0, 0)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawPath(shape, rim)
    }

    private fun roundedTile(s: Float) = Path().apply { addRoundRect(RectF(0f, 0f, s, s), s * CORNER, s * CORNER, Path.Direction.CW) }

    private fun linear(top: Float, bottom: Float, from: Int, to: Int) = LinearGradient(0f, top, 0f, bottom, from, to, Shader.TileMode.CLAMP)

    private fun solid(color: Int) = Paint(FLAGS).apply { this.color = color }
}
