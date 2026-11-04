package id.nextfl.app.visual

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import id.nextfl.core.model.VisualSettings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sprite for additive glows. */
class GlowSprite {
    val bitmap: Bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { sprite ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                SIZE / 2f, SIZE / 2f, SIZE / 2f,
                intArrayOf(Color.WHITE, Color.argb(150, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.18f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(sprite).drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint)
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.PLUS }
    private val source = Rect(0, 0, SIZE, SIZE)
    private val target = RectF()
    private var tint = Color.WHITE

    fun draw(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Float) {
        if (alpha <= 0.01f || radius <= 0.2f) return
        if (color != tint) {
            tint = color
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        target.set(x - radius, y - radius, x + radius, y + radius)
        canvas.drawBitmap(bitmap, source, target, paint)
    }

    private companion object {
        const val SIZE = 64
    }
}

/** Cached dimmed, blurred backdrop. */
class BackgroundLayer {
    private var cached: Bitmap? = null
    private var cacheKey: List<Any?> = emptyList()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val verts = FloatArray((MESH + 1) * (MESH + 1) * 2)

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        image: Bitmap?,
        settings: VisualSettings,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        angle: Float = 0f,
        ripple: RippleField? = null,
    ) {
        val key = listOf(System.identityHashCode(image), width, height, settings.backgroundDim, settings.backgroundBlur, settings.grayscale)
        if (key != cacheKey || cached == null) {
            cached?.recycle()
            cached = build(width, height, image, settings)
            cacheKey = key
        }
        val bitmap = cached ?: return
        if (angle != 0f || ripple?.isActive == true) {
            drawMesh(canvas, bitmap, width, height, zoom, offsetX, offsetY, angle, ripple)
            return
        }
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(zoom, zoom)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
        canvas.restore()
    }

    // Swing and water waves via mesh
    private fun drawMesh(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int, zoom: Float, offsetX: Float, offsetY: Float, angle: Float, ripple: RippleField?) {
        val radians = Math.toRadians(angle.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val aspect = max(width, height).toFloat() / min(width, height)
        // Grow just enough to hide corners
        val scale = zoom * max(1f, (abs(c) + aspect * abs(s)) / OVERSCAN)
        val halfW = bitmap.width / 2f
        val halfH = bitmap.height / 2f
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY
        var k = 0
        for (j in 0..MESH) {
            for (i in 0..MESH) {
                var px = i * bitmap.width / MESH.toFloat() - halfW
                var py = j * bitmap.height / MESH.toFloat() - halfH
                val distance = hypot(px, py)
                if (ripple != null && distance > 0f) {
                    val push = ripple.displacement(distance) / distance
                    px += px * push
                    py += py * push
                }
                px *= scale
                py *= scale
                verts[k++] = cx + px * c - py * s
                verts[k++] = cy + px * s + py * c
            }
        }
        canvas.drawBitmapMesh(bitmap, MESH, MESH, verts, 0, null, 0, paint)
    }

    private fun build(width: Int, height: Int, image: Bitmap?, settings: VisualSettings): Bitmap {
        val w = max(1, (width * OVERSCAN).roundToInt())
        val h = max(1, (height * OVERSCAN).roundToInt())
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (image == null) drawDefault(canvas) else drawImage(canvas, w, h, image, settings)
        drawVignette(canvas, w, h)
        return result
    }

    private fun drawImage(canvas: Canvas, w: Int, h: Int, image: Bitmap, settings: VisualSettings) {
        val blurScale = 1f / (1f + settings.backgroundBlur / BLUR_DIVISOR)
        val smallW = max(1, (w * blurScale).roundToInt())
        val smallH = max(1, (h * blurScale).roundToInt())
        val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply {
            if (settings.grayscale) setSaturation(0f)
            val brightness = 1f - settings.backgroundDim
            postConcat(ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) })
        }
        val filtered = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        val scale = max(smallW.toFloat() / image.width, smallH.toFloat() / image.height)
        val drawW = image.width * scale
        val drawH = image.height * scale
        Canvas(small).drawBitmap(image, null, RectF((smallW - drawW) / 2, (smallH - drawH) / 2, (smallW + drawW) / 2, (smallH + drawH) / 2), filtered)
        canvas.drawBitmap(small, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        small.recycle()
    }

    private fun drawDefault(canvas: Canvas) {
        canvas.drawColor(DEFAULT_BACKGROUND)
    }

    private fun drawVignette(canvas: Canvas, w: Int, h: Int) {
        canvas.drawPaint(Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, max(w, h) * 0.75f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        })
    }

    companion object {
        const val OVERSCAN = 1.08f
        private const val MESH = 48
        private val DEFAULT_BACKGROUND = Color.parseColor("#131517")
        private const val BLUR_DIVISOR = 3f
    }
}
