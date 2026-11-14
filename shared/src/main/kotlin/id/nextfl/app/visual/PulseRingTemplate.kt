package id.nextfl.app.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.model.SocialPlatform
import id.nextfl.core.model.VisualSettings
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Circular DJ logo badge. */
class LogoBadge(typeface: Typeface) {
    private var logos: List<Bitmap> = emptyList()
    private var transparent = BooleanArray(0)
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

    fun set(next: List<Bitmap>) {
        if (next == logos) return
        logos = next
        transparent = BooleanArray(next.size) { hasTransparentEdges(next[it]) }
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, ringColor: Int, cycle: Rotation) {
        fill.color = Color.argb(110, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        if (logos.isEmpty()) drawPlaceholder(canvas, cx, cy, radius) else crossfade(canvas, cx, cy, radius, cycle)
        canvas.restore()
        stroke.color = ringColor
        stroke.strokeWidth = radius * 0.03f
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    // Opaque incoming covers outgoing
    private fun crossfade(canvas: Canvas, cx: Float, cy: Float, radius: Float, cycle: Rotation) {
        val current = cycle.current(logos.size)
        val mix = cycle.mix(logos.size)
        if (mix <= 0f) {
            drawLogo(canvas, current, cx, cy, radius, 1f)
            return
        }
        val next = cycle.next(logos.size)
        drawLogo(canvas, current, cx, cy, radius, if (transparent[next]) 1f - mix else 1f)
        drawLogo(canvas, next, cx, cy, radius, mix)
    }

    // Transparent logos fit circle
    private fun drawLogo(canvas: Canvas, index: Int, cx: Float, cy: Float, radius: Float, alpha: Float) {
        val logo = logos[index]
        val box = radius * 2
        val scale = if (transparent[index]) min(box / logo.width, box / logo.height) else max(box / logo.width, box / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        target.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        image.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
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
    // Current and incoming layers
    private val backgrounds = arrayOf(BackgroundLayer(), BackgroundLayer())
    private val wipe = WipeMask()
    private val logoCycle = Rotation(LOGO_SECONDS, LOGO_FADE_SECONDS)
    private val backgroundCycle = Rotation(BACKGROUND_SECONDS, BACKGROUND_FADE_SECONDS)
    private var clock = 0.0
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
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.typeface = typeface
        letterSpacing = 0.04f
    }
    private val socialIcons = SocialIcons()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val flashPaint = Paint()
    private var backgroundImages: List<Bitmap> = emptyList()
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
    private var titleSource = ""
    private var extraSource: List<String> = emptyList()
    private var titleList: List<String> = emptyList()

    /** Lists rotate when longer than one. */
    fun setImages(backgrounds: List<Bitmap>, logos: List<Bitmap>) {
        backgroundImages = backgrounds
        logo.set(logos)
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
        zoom = 1f + frame.drive * 0.03f * settings.sensitivity + frame.kick * 0.02f
        update(frame, dt, settings)
        if (glitch.isSplitting) glitch.drawSplit(canvas, unit, settings.glitchAmount, drawScene) else scene(canvas)
        glitch.drawSlices(canvas, width, height, settings.ringColor.toInt(), settings.glitchGlare, drawScene)
        if (flash > 0.01f) {
            flashPaint.color = Color.argb((flash * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }

    // State advances once per frame
    private fun update(frame: AudioFrame, dt: Float, settings: VisualSettings) {
        clock += dt
        logoCycle.at(clock)
        backgroundCycle.at(clock)
        particles.update(settings.particleCount, dt, frame.energy, frame.kick)
        comets.update(if (settings.streaks) settings.cometCount else 0, dt, frame.beat, frame.beatStrength, frame.energy, width, height)
        if (settings.showHills) hills.update(frame.beat, frame.beatStrength, frame.drive, dt)
        ripples.update(frame.beat, frame.beatStrength, settings.rippleAmount, dt, hypot(width.toFloat(), height.toFloat()) / 2, unit)
        pendulum.update(frame.beat, settings.pendulumAmount, dt)
        glitch.update(frame.beat, frame.beatStrength, settings.glitchAmount, dt)
    }

    // Pure drawing; ripple and swing hit background only
    private fun scene(canvas: Canvas) {
        val particleColor = settings.particleColor.toInt()
        val bgX = shakeX * 0.5f
        val bgY = shakeY * 0.5f
        drawBackground(canvas, bgX, bgY)
        ripples.drawCrests(canvas, width / 2f + bgX, height / 2f + bgY, settings.ringColor.toInt())
        particles.draw(canvas, sprite, width, height, particleColor, kick)
        comets.draw(canvas, particleColor, unit, settings.cometGlare)
        centerGroup(canvas)
    }

    // Incoming enters with soft edge
    private fun drawBackground(canvas: Canvas, offsetX: Float, offsetY: Float) {
        val images = backgroundImages
        val count = images.size
        val cycle = backgroundCycle
        val front = if (count > 1) (cycle.slot and 1L).toInt() else 0
        backgrounds[front].draw(canvas, width, height, images.getOrNull(cycle.current(count)), settings, zoom, offsetX, offsetY, pendulum.angle, ripples)
        val mix = cycle.mix(count)
        if (mix <= 0f) return
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        backgrounds[1 - front].draw(canvas, width, height, images[cycle.next(count)], settings, zoom, offsetX, offsetY, pendulum.angle, ripples)
        wipe.apply(canvas, width, height, mix, settings.backgroundSlide)
        canvas.restoreToCount(layer)
    }

    private fun centerGroup(canvas: Canvas) {
        val cx = centerX()
        val cy = centerY()
        val radius = unit * RING_RADIUS
        val ringColor = settings.ringColor.toInt()
        val pulsed = radius * (1f + kick * LOGO_PULSE * settings.sensitivity)
        if (settings.showHills) hills.draw(canvas, cx, cy, pulsed, ringColor, settings.sensitivity, settings.hillGlare)
        if (settings.showLogo) logo.draw(canvas, cx, cy, pulsed, ringColor, logoCycle)
        val titleY = cy + radius * 1.72f
        val texts = titles()
        drawTitles(canvas, texts, cx, titleY)
        val socialY = if (texts.isEmpty()) titleY - unit * TITLE_SIZE * 0.36f else titleY + unit * SOCIAL_BELOW_TITLE
        if (settings.showSocial) drawSocial(canvas, cx, socialY)
    }

    // Icons then username, centered row
    private fun drawSocial(canvas: Canvas, x: Float, y: Float) {
        val chosen = settings.socialPlatforms
        val platforms = SocialPlatform.entries
        var iconCount = 0
        for (index in platforms.indices) if (platforms[index] in chosen) iconCount++
        val name = settings.socialUsername.trim()
        if (iconCount == 0 && name.isEmpty()) return
        val size = (unit * SOCIAL_ICON).roundToInt().coerceAtLeast(MIN_ICON_PX)
        val gap = unit * SOCIAL_GAP
        handle.textSize = unit * HANDLE_SIZE
        handle.setShadowLayer(unit * 0.01f, 0f, 0f, Color.argb(160, 0, 0, 0))
        val nameGap = if (iconCount > 0 && name.isNotEmpty()) gap * 1.6f else 0f
        val nameWidth = if (name.isEmpty()) 0f else handle.measureText(name)
        val iconsWidth = iconCount * size + max(0, iconCount - 1) * gap
        var left = x - (iconsWidth + nameGap + nameWidth) / 2
        // Indexed loop avoids per-frame allocation
        for (index in platforms.indices) {
            val platform = platforms[index]
            if (platform !in chosen) continue
            val bitmap = socialIcons.bitmap(platform, size)
            canvas.drawBitmap(bitmap, left + (size - bitmap.width) / 2f, y - bitmap.height / 2f, iconPaint)
            left += size + gap
        }
        if (name.isNotEmpty()) {
            val start = if (iconCount == 0) left else left - gap + nameGap
            canvas.drawText(name, start, y + handle.textSize * 0.36f, handle)
        }
    }

    private fun centerX() = width / 2f + shakeX

    private fun centerY() = height / 2f + shakeY

    private fun react(frame: AudioFrame, dt: Float, settings: VisualSettings) {
        if (frame.beat && settings.shake) shake = max(shake, frame.beatStrength)
        shake *= exp(-dt * SHAKE_DECAY)
        flash = max(flash * exp(-dt * FLASH_DECAY), if (frame.beat) frame.beatStrength * FLASH_AMOUNT else 0f)
    }

    // Rebuilt only when text changes
    private fun titles(): List<String> {
        if (settings.title != titleSource || settings.collabTexts != extraSource) {
            titleSource = settings.title
            extraSource = settings.collabTexts
            titleList = (listOf(settings.title) + settings.collabTexts).filter { it.isNotBlank() }.map { it.uppercase() }
        }
        return titleList
    }

    // Swaps with logo: out, then in
    private fun drawTitles(canvas: Canvas, texts: List<String>, x: Float, y: Float) {
        val count = texts.size
        if (count == 0) return
        val mix = logoCycle.mix(count)
        when {
            mix <= 0f -> drawTitle(canvas, texts[logoCycle.current(count)], x, y, 1f)
            mix < 0.5f -> drawTitle(canvas, texts[logoCycle.current(count)], x, y, 1f - mix * 2)
            else -> drawTitle(canvas, texts[logoCycle.next(count)], x, y, mix * 2 - 1)
        }
    }

    private fun drawTitle(canvas: Canvas, text: String, x: Float, y: Float, alpha: Float) {
        val level = alpha.coerceIn(0f, 1f)
        title.textSize = unit * TITLE_SIZE
        title.color = Color.argb((level * 255).roundToInt(), 255, 255, 255)
        // Translucent shadow ignores paint alpha
        title.setShadowLayer(unit * 0.01f, 0f, 0f, Color.argb((level * SHADOW_ALPHA).roundToInt(), 0, 0, 0))
        canvas.drawText(text, x, y, title)
    }

    private companion object {
        const val RING_RADIUS = 0.19f
        const val SHAKE_AMOUNT = 0.03f
        const val SHAKE_DECAY = 9f
        const val FLASH_DECAY = 8f
        const val FLASH_AMOUNT = 0.07f
        const val LOGO_PULSE = 0.05f
        const val TITLE_SIZE = 0.045f
        const val SOCIAL_BELOW_TITLE = 0.06f
        const val SOCIAL_ICON = 0.056f
        const val SOCIAL_GAP = 0.016f
        const val HANDLE_SIZE = 0.034f
        const val MIN_ICON_PX = 8
        const val SHADOW_ALPHA = 160
        const val LOGO_SECONDS = 4.0
        const val LOGO_FADE_SECONDS = 0.8
        const val BACKGROUND_SECONDS = 5.0
        const val BACKGROUND_FADE_SECONDS = 1.2
    }
}
