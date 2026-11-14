package id.nextfl.app.visual

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import id.nextfl.core.model.SlideDirection
import kotlin.math.floor

/** Timed loop, fades into next. */
class Rotation(private val holdSeconds: Double, private val fadeSeconds: Double) {
    var slot = 0L
        private set
    private var progress = 0f

    // Fade ends exactly on slot change
    fun at(time: Double) {
        slot = floor(time / holdSeconds).toLong()
        val inSlot = time - slot * holdSeconds
        progress = ((inSlot - (holdSeconds - fadeSeconds)) / fadeSeconds).coerceIn(0.0, 1.0).toFloat()
    }

    fun current(count: Int): Int = if (count <= 0) 0 else (slot % count).toInt()

    fun next(count: Int): Int = if (count <= 0) 0 else ((slot + 1) % count).toInt()

    /** Zero unless something to swap. */
    fun mix(count: Int): Float = if (count > 1) progress else 0f
}

/** Soft edge reveals incoming layer. */
class WipeMask {
    private val horizontal = maskPaint(LinearGradient(0f, 0f, 1f, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP))
    private val vertical = maskPaint(LinearGradient(0f, 0f, 0f, 1f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP))

    // Call inside a layer
    fun apply(canvas: Canvas, width: Int, height: Int, progress: Float, from: SlideDirection) {
        val across = from == SlideDirection.LEFT || from == SlideDirection.RIGHT
        val length = (if (across) width else height).toFloat()
        val soft = length * SOFT_EDGE
        val travel = (length + soft) * smooth(progress)
        val far = from == SlideDirection.RIGHT || from == SlideDirection.BOTTOM
        // Gradient 0..1 spans soft band
        val origin = if (far) length - travel else travel
        val scale = if (far) soft else -soft
        val start = -origin / scale
        val end = (length - origin) / scale
        val low = minOf(start, end)
        val high = maxOf(start, end)
        canvas.save()
        if (across) {
            canvas.translate(origin, 0f)
            canvas.scale(scale, 1f)
            canvas.drawRect(low, 0f, high, height.toFloat(), horizontal)
        } else {
            canvas.translate(0f, origin)
            canvas.scale(1f, scale)
            canvas.drawRect(0f, low, width.toFloat(), high, vertical)
        }
        canvas.restore()
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private companion object {
        const val SOFT_EDGE = 0.35f

        fun maskPaint(gradient: Shader) = Paint().apply {
            shader = gradient
            blendMode = BlendMode.DST_IN
        }
    }
}
