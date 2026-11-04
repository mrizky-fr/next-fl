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
import kotlin.math.hypot
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
        stroke.strokeWidth = radius * 0.03f
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    // Transparent logos fill circle
    private fun drawLogo(canvas: Canvas, logo: Bitmap, cx: Float, cy: Float, radius: Float) {
        val box = radius * 2
        val scale = if (transparent) min(box / logo.width, box / logo.height) else max(box / logo.width, box / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        target.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        canvas.drawBitmap(logo, null, target, image)
    }

    private fun drawPlaceholder(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        text.textSize = radius * PLACEHOLDER_SIZE
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
        const val PLACEHOLDER_SIZE = 0.9f
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
    private val hills = BeatHills()
    private val ripples = RippleField()
    private val pendulum = Pendulum()
    private val glitch = Glitch()
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

    // Per-frame scene, redrawn by glitch
    private var settings = VisualSettings()
    private var width = 0
    private var height = 0
    private var unit = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    private var zoom = 1f
    private var kick = 0f
    private val drawScene: (Canvas) -> Unit = { scene(it) }

    fun setImages(background: Bitmap?, logoImage: Bitmap?) {
        backgroundImage = background
        logo.set(logoImage)
    }

    fun draw(canvas: Canvas, width: Int, height: Int, frame: AudioFrame, dt: Float, settings: VisualSettings) {
        if (width <= 0 || height <= 0) return
        this.settings = settings
        this.width = width
        this.height = height
        unit = min(width, height).toFloat()
        kick = frame.kick
        react(frame, dt, settings)
        shakeX = (random.nextFloat() - 0.5f) * shake * unit * SHAKE_AMOUNT
        shakeY = (random.nextFloat() - 0.5f) * shake * unit * SHAKE_AMOUNT
        zoom = 1f + frame.bass * 0.03f * settings.sensitivity + frame.kick * 0.02f
        update(frame, dt, settings)
        if (glitch.isSplitting) glitch.drawSplit(canvas, unit, settings.glitchAmount, drawScene) else scene(canvas)
        glitch.drawSlices(canvas, width, height, settings.ringColor.toInt(), drawScene)
        if (flash > 0.01f) {
            flashPaint.color = Color.argb((flash * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }

    // State advances once per frame
    private fun update(frame: AudioFrame, dt: Float, settings: VisualSettings) {
        particles.update(settings.particleCount, dt, frame.energy, frame.kick)
        comets.update(if (settings.streaks) COMET_COUNT else 0, dt, frame.beat, frame.beatStrength, frame.energy, width, height)
        if (settings.showHills) hills.update(frame.beat, frame.beatStrength, frame.bass, dt)
        ripples.update(frame.beat, frame.beatStrength, settings.rippleAmount, dt, hypot(width.toFloat(), height.toFloat()) / 2, unit)
        pendulum.update(frame.beat, frame.beatStrength, settings.pendulumAmount, dt)
        glitch.update(frame.beat, frame.beatStrength, settings.glitchAmount, dt)
    }

    // Pure drawing; ripple and swing hit background only
    private fun scene(canvas: Canvas) {
        val particleColor = settings.particleColor.toInt()
        val bgX = shakeX * 0.5f
        val bgY = shakeY * 0.5f
        background.draw(canvas, width, height, backgroundImage, settings, zoom, bgX, bgY, pendulum.angle, ripples)
        ripples.drawCrests(canvas, width / 2f + bgX, height / 2f + bgY, settings.ringColor.toInt())
        particles.draw(canvas, sprite, width, height, particleColor, kick)
        comets.draw(canvas, particleColor, unit)
        centerGroup(canvas)
    }

    private fun centerGroup(canvas: Canvas) {
        val cx = centerX()
        val cy = centerY()
        val radius = unit * RING_RADIUS
        val ringColor = settings.ringColor.toInt()
        val pulsed = radius * (1f + kick * LOGO_PULSE * settings.sensitivity)
        if (settings.showHills) hills.draw(canvas, cx, cy, pulsed, ringColor, settings.sensitivity)
        if (settings.showLogo) logo.draw(canvas, cx, cy, pulsed, ringColor)
        drawTitle(canvas, settings.title, cx, cy + radius * 1.72f, unit)
    }

    private fun centerX() = width / 2f + shakeX

    private fun centerY() = height / 2f + shakeY

    private fun react(frame: AudioFrame, dt: Float, settings: VisualSettings) {
        if (frame.beat && settings.shake) shake = max(shake, frame.beatStrength)
        shake *= exp(-dt * SHAKE_DECAY)
        flash = max(flash * exp(-dt * FLASH_DECAY), if (frame.beat) frame.beatStrength * FLASH_AMOUNT else 0f)
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
        const val LOGO_PULSE = 0.05f
    }
}
