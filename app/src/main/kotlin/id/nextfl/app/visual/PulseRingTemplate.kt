package id.nextfl.app.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.model.VisualSettings
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Circular DJ logo badge. */
class LogoBadge(typeface: Typeface) {
    private var bitmap: Bitmap? = null
    private var transparent = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
    }
    private val clip = Path()
    private val target = RectF()

    fun set(next: Bitmap?) {
        if (next === bitmap) return
        bitmap = next
        transparent = next?.let(::hasTransparentEdges) ?: false
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, ringColor: Int) {
        fill.color = Color.argb(110, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        val logo = bitmap
        if (logo == null) drawPlaceholder(canvas, cx, cy, radius) else drawLogo(canvas, logo, cx, cy, radius)
        canvas.restore()
        stroke.color = ringColor
        stroke.alpha = 235
        stroke.strokeWidth = radius * 0.03f
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    // Transparent logos fit inside
    private fun drawLogo(canvas: Canvas, logo: Bitmap, cx: Float, cy: Float, radius: Float) {
        val box = radius * 2 * if (transparent) CONTAIN_SCALE else 1f
        val scale = if (transparent) min(box / logo.width, box / logo.height) else max(box / logo.width, box / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        target.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.drawBitmap(logo, null, target, image)
    }

    private fun drawPlaceholder(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        text.textSize = radius * 0.62f
        canvas.drawText("DJ", cx, cy + text.textSize * 0.36f, text)
    }

    private fun hasTransparentEdges(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width - 1
        val h = bitmap.height - 1
        val samples = (0..SAMPLES).flatMap { i ->
            val t = i.toFloat() / SAMPLES
            listOf((t * w).toInt() to 0, (t * w).toInt() to h, 0 to (t * h).toInt(), w to (t * h).toInt())
        }
        return samples.any { (x, y) -> Color.alpha(bitmap.getPixel(x, y)) < OPAQUE }
    }

    private companion object {
        const val CONTAIN_SCALE = 0.84f
        const val SAMPLES = 12
        const val OPAQUE = 250
    }
}

/** Template one: pulse ring. */
class PulseRingTemplate(typeface: Typeface) {
    private val sprite = GlowSprite()
    private val background = BackgroundLayer()
    private val particles = ParticleField()
    private val comets = CometField()
    private val ring = SpectrumRing()
    private val logo = LogoBadge(typeface)
    private val random = Random(3)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        this.typeface = typeface
        letterSpacing = 0.12f
    }
    private val flashPaint = Paint()
    private var backgroundImage: Bitmap? = null
    private var shake = 0f
    private var flash = 0f
    private var rotation = 0f

    fun setImages(background: Bitmap?, logoImage: Bitmap?) {
        backgroundImage = background
        logo.set(logoImage)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, frame: AudioFrame, dt: Float, settings: VisualSettings) {
        if (width <= 0 || height <= 0) return
        val unit = min(width, height).toFloat()
        val ringColor = settings.ringColor.toInt()
        val particleColor = settings.particleColor.toInt()
        react(frame, dt, settings)
        val shakeX = (random.nextFloat() - 0.5f) * shake * unit * SHAKE_AMOUNT
        val shakeY = (random.nextFloat() - 0.5f) * shake * unit * SHAKE_AMOUNT
        val zoom = 1f + frame.bass * 0.03f * settings.sensitivity + frame.kick * 0.02f

        background.draw(canvas, width, height, backgroundImage, settings, zoom, shakeX * 0.5f, shakeY * 0.5f)
        particles.update(settings.particleCount, dt, frame.energy, frame.kick)
        particles.draw(canvas, sprite, width, height, particleColor, frame.kick)

        val cx = width / 2f + shakeX
        val cy = height / 2f + shakeY
        val radius = unit * RING_RADIUS
        comets.update(if (settings.streaks) COMET_COUNT else 0, dt, frame.beat, frame.beatStrength, frame.energy, width, height)
        comets.draw(canvas, sprite, particleColor, unit)

        ring.update(frame.bands, dt)
        ring.draw(canvas, cx, cy, radius, ringColor, settings.sensitivity, rotation)
        logo.draw(canvas, cx, cy, radius * (1f + frame.kick * 0.05f * settings.sensitivity), ringColor)
        drawTitle(canvas, settings.title, cx, cy + radius * 1.72f, unit)

        if (flash > 0.01f) {
            flashPaint.color = Color.argb((flash * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }

    private fun react(frame: AudioFrame, dt: Float, settings: VisualSettings) {
        if (frame.beat && settings.shake) shake = max(shake, frame.beatStrength)
        shake *= exp(-dt * SHAKE_DECAY)
        flash = max(flash * exp(-dt * FLASH_DECAY), if (frame.beat) frame.beatStrength * FLASH_AMOUNT else 0f)
        rotation += dt * (ROTATION_SPEED + frame.energy * 0.4f)
    }

    private fun drawTitle(canvas: Canvas, text: String, x: Float, y: Float, unit: Float) {
        if (text.isBlank()) return
        title.textSize = unit * 0.045f
        title.setShadowLayer(unit * 0.01f, 0f, 0f, Color.argb(160, 0, 0, 0))
        canvas.drawText(text.uppercase(), x, y, title)
    }

    private companion object {
        const val RING_RADIUS = 0.19f
        const val SHAKE_AMOUNT = 0.03f
        const val SHAKE_DECAY = 9f
        const val FLASH_DECAY = 8f
        const val FLASH_AMOUNT = 0.07f
        const val COMET_COUNT = 6
        const val ROTATION_SPEED = 0.05f
    }
}
