package id.nextfl.app.visual

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import id.nextfl.core.analysis.BeatSwing
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Beat waves that ripple the background. */
class RippleField {
    private class Ring(var age: Float, val strength: Float)

    private val rings = ArrayList<Ring>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var amount = 0f
    private var reach = 1f
    private var unit = 1f

    val isActive: Boolean get() = amount > 0f && rings.isNotEmpty()

    fun update(beat: Boolean, strength: Float, amount: Float, dt: Float, reach: Float, unit: Float) {
        this.amount = amount
        this.reach = reach
        this.unit = unit
        for (ring in rings) ring.age += dt
        rings.removeAll { it.age > LIFE_SECONDS }
        if (beat && amount > 0f) {
            rings.add(Ring(0f, strength))
            if (rings.size > MAX_RINGS) rings.removeAt(0)
        }
    }

    /** Radial push for background mesh. */
    fun displacement(distance: Float): Float {
        if (!isActive) return 0f
        val width = unit * WAVE_WIDTH
        val wavelength = unit * WAVELENGTH
        var sum = 0f
        for (ring in rings) {
            val offset = distance - front(ring)
            if (abs(offset) > width * 3) continue
            val envelope = exp(-(offset / width) * (offset / width))
            sum += sin(offset / wavelength * TWO_PI) * envelope * fade(ring)
        }
        return sum * unit * MAX_DISPLACEMENT * amount
    }

    // Faint crests, background layer only
    fun drawCrests(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        if (!isActive) return
        stroke.color = color
        stroke.strokeWidth = unit * CREST_WIDTH
        for (ring in rings) {
            val front = front(ring)
            for (crest in 0 until CRESTS) {
                val r = front - crest * unit * WAVELENGTH
                if (r <= 0f) continue
                stroke.alpha = (fade(ring) * amount * CREST_ALPHA * (1f - crest * CREST_FALLOFF)).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, r, stroke)
            }
        }
    }

    private fun progress(ring: Ring) = (ring.age / LIFE_SECONDS).coerceIn(0f, 1f)

    private fun front(ring: Ring) = reach * (1f - (1f - progress(ring)).pow(3))

    private fun fade(ring: Ring) = (1f - progress(ring)).pow(1.5f) * ring.strength

    private companion object {
        const val LIFE_SECONDS = 1.6f
        const val MAX_RINGS = 6
        const val WAVE_WIDTH = 0.12f
        const val WAVELENGTH = 0.08f
        const val MAX_DISPLACEMENT = 0.02f
        const val CRESTS = 2
        const val CREST_ALPHA = 110f
        const val CREST_WIDTH = 0.006f
        const val CREST_FALLOFF = 0.5f
        const val TWO_PI = 6.2831855f
    }
}

/** Beat-locked swing, degrees. */
class Pendulum {
    private val swing = BeatSwing()
    var angle = 0f
        private set

    fun update(beat: Boolean, amount: Float, dt: Float) {
        swing.update(beat, dt)
        angle = swing.value * MAX_DEGREES * amount
    }

    private companion object {
        // Background only, keeps zoom small
        const val MAX_DEGREES = 10f
    }
}

/** Beat-triggered slices and RGB split. */
class Glitch(private val random: Random = Random(19)) {
    private val offsets = FloatArray(MAX_BANDS)
    private val tops = FloatArray(MAX_BANDS)
    private val heights = FloatArray(MAX_BANDS)
    private val starts = FloatArray(MAX_BANDS)
    private var bands = 0
    private var reseed = 0f
    private val block = Paint()
    private val red = splitPaint(1f, 0f, 0f).apply { blendMode = BlendMode.PLUS }
    private val cyan = splitPaint(0f, 1f, 1f)
    private val blockGlare = StreakSprite()
    private val redGlare = StreakSprite()
    private val cyanGlare = StreakSprite()
    var level = 0f
        private set

    fun update(beat: Boolean, strength: Float, amount: Float, dt: Float) {
        if (beat && amount > 0f) level = maxOf(level, strength * (0.5f + amount * 0.5f))
        level *= exp(-dt * DECAY)
        if (level < MIN_LEVEL || amount <= 0f) {
            level = 0f
            bands = 0
            return
        }
        reseed -= dt
        if (reseed <= 0f) {
            reseed = RESEED_SECONDS
            shuffle(amount)
        }
    }

    val isSplitting: Boolean get() = level > 0f

    // Whole frame: cyan left, red right
    fun drawSplit(canvas: Canvas, unit: Float, amount: Float, scene: (Canvas) -> Unit) {
        val shift = unit * MAX_SPLIT * level * amount
        layer(canvas, cyan, -shift, scene)
        layer(canvas, red, shift, scene)
    }

    // Shifted slices plus error blocks
    fun drawSlices(canvas: Canvas, width: Int, height: Int, color: Int, glare: Float, scene: (Canvas) -> Unit) {
        if (level <= 0f) return
        for (i in 0 until bands) {
            val top = tops[i] * height
            canvas.save()
            canvas.clipRect(0f, top, width.toFloat(), top + heights[i] * height)
            canvas.translate(offsets[i] * width * level, 0f)
            scene(canvas)
            canvas.restore()
        }
        block.color = color
        block.alpha = (level * BLOCK_ALPHA).toInt().coerceIn(0, 255)
        for (i in 0 until bands step 2) {
            val top = tops[i] * height
            val left = blockLeft(i, width)
            canvas.drawRect(left, top, left + width * BLOCK_WIDTH, top + height * BLOCK_HEIGHT, block)
        }
        if (glare > 0f) drawGlare(canvas, width, height, color, glare)
    }

    // Streaks follow each slice shift
    private fun drawGlare(canvas: Canvas, width: Int, height: Int, color: Int, glare: Float) {
        val unit = min(width, height).toFloat()
        val alpha = (glare * level * GLARE_ALPHA).coerceAtMost(1f)
        val reach = width * glare * level
        for (i in 0 until bands) {
            val right = offsets[i] >= 0f
            val y = (tops[i] + heights[i] / 2) * height
            val thickness = min(heights[i] * height, unit * MAX_GLARE_THICKNESS)
            val sprite = if (right) redGlare else cyanGlare
            sprite.draw(canvas, starts[i] * width, y, direction(right), reach * SLICE_GLARE, thickness, if (right) Color.RED else Color.CYAN, alpha)
        }
        for (i in 0 until bands step 2) {
            val right = offsets[i] >= 0f
            val left = blockLeft(i, width)
            val x = if (right) left + width * BLOCK_WIDTH else left
            val y = (tops[i] + BLOCK_HEIGHT / 2) * height
            blockGlare.draw(canvas, x, y, direction(right), reach * BLOCK_GLARE, height * BLOCK_HEIGHT * BLOCK_GLARE_THICKNESS, color, alpha)
        }
    }

    private fun blockLeft(index: Int, width: Int) = (0.5f + offsets[index]).coerceIn(0f, 1f) * width

    private fun direction(right: Boolean) = if (right) 0f else HALF_TURN_DEG

    private fun shuffle(amount: Float) {
        bands = (MIN_BANDS + amount * (MAX_BANDS - MIN_BANDS)).toInt()
        for (i in 0 until bands) {
            tops[i] = random.nextFloat()
            heights[i] = 0.01f + random.nextFloat() * 0.08f
            offsets[i] = (random.nextFloat() - 0.5f) * MAX_SHIFT * amount
            starts[i] = GLARE_START_MIN + random.nextFloat() * GLARE_START_SPREAD
        }
    }

    private fun layer(canvas: Canvas, paint: Paint, dx: Float, scene: (Canvas) -> Unit) {
        canvas.saveLayer(null, paint)
        canvas.translate(dx, 0f)
        scene(canvas)
        canvas.restore()
    }

    private companion object {
        const val DECAY = 9f
        const val MIN_LEVEL = 0.02f
        const val RESEED_SECONDS = 0.05f
        const val MIN_BANDS = 2
        const val MAX_BANDS = 8
        const val MAX_SHIFT = 0.12f
        const val MAX_SPLIT = 0.025f
        const val BLOCK_ALPHA = 140f
        const val BLOCK_WIDTH = 0.06f
        const val BLOCK_HEIGHT = 0.008f
        const val GLARE_ALPHA = 0.9f
        const val SLICE_GLARE = 0.35f
        const val BLOCK_GLARE = 0.22f
        const val BLOCK_GLARE_THICKNESS = 4f
        const val MAX_GLARE_THICKNESS = 0.02f
        const val GLARE_START_MIN = 0.1f
        const val GLARE_START_SPREAD = 0.8f
        const val HALF_TURN_DEG = 180f

        // Keeps one color channel only
        fun splitPaint(r: Float, g: Float, b: Float) = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        r, 0f, 0f, 0f, 0f,
                        0f, g, 0f, 0f, 0f,
                        0f, 0f, b, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }
}
