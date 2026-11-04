package id.nextfl.app.visual

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Water rings spawned on beats. */
class RippleField {
    private class Ring(var age: Float, val strength: Float)

    private val rings = ArrayList<Ring>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun update(beat: Boolean, strength: Float, amount: Float, dt: Float) {
        for (ring in rings) ring.age += dt
        rings.removeAll { it.age > LIFE_SECONDS }
        if (beat && amount > 0f) {
            rings.add(Ring(0f, strength))
            if (rings.size > MAX_RINGS) rings.removeAt(0)
        }
    }

    // Main ring plus fading echoes
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, reach: Float, color: Int, amount: Float) {
        if (amount <= 0f) return
        stroke.color = color
        for (ring in rings) {
            val t = (ring.age / LIFE_SECONDS).coerceIn(0f, 1f)
            val eased = 1f - (1f - t).pow(3)
            val front = radius + (reach - radius) * eased
            for (echo in 0 until ECHOES) {
                val r = front - echo * radius * ECHO_SPACING
                if (r <= radius) continue
                val fade = (1f - t).pow(1.5f) * (1f - echo * ECHO_FADE)
                stroke.alpha = (fade * amount * ring.strength * MAX_ALPHA).toInt().coerceIn(0, 255)
                stroke.strokeWidth = radius * (RING_WIDTH * (1f - t) + MIN_WIDTH) * (0.5f + amount)
                canvas.drawCircle(cx, cy, r, stroke)
            }
        }
    }

    private companion object {
        const val LIFE_SECONDS = 1.4f
        const val MAX_RINGS = 6
        const val ECHOES = 3
        const val ECHO_SPACING = 0.18f
        const val ECHO_FADE = 0.35f
        const val MAX_ALPHA = 230f
        const val RING_WIDTH = 0.05f
        const val MIN_WIDTH = 0.006f
    }
}

/** Beat-kicked damped swing, degrees. */
class Pendulum {
    private var velocity = 0f
    private var side = 1f
    var angle = 0f
        private set

    fun update(beat: Boolean, strength: Float, amount: Float, dt: Float) {
        if (beat && amount > 0f) {
            velocity += side * strength * KICK * amount
            side = -side
        }
        var remaining = dt
        while (remaining > 0f) {
            val step = min(remaining, MAX_STEP_SECONDS)
            velocity += (-STIFFNESS * angle - DAMPING * velocity) * step
            angle = (angle + velocity * step).coerceIn(-MAX_DEGREES, MAX_DEGREES)
            remaining -= step
        }
    }

    private companion object {
        // Swings once per two beats
        const val STIFFNESS = 45f
        const val DAMPING = 2f
        const val KICK = 90f
        const val MAX_DEGREES = 25f
        const val MAX_STEP_SECONDS = 1f / 240f
    }
}

/** Beat-triggered slices and RGB split. */
class Glitch(private val random: Random = Random(19)) {
    private val offsets = FloatArray(MAX_BANDS)
    private val tops = FloatArray(MAX_BANDS)
    private val heights = FloatArray(MAX_BANDS)
    private var bands = 0
    private var reseed = 0f
    private val block = Paint()
    private val red = splitPaint(1f, 0f, 0f)
    private val cyan = splitPaint(0f, 1f, 1f)
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

    // Red and cyan ghosts behind
    fun drawSplit(canvas: Canvas, unit: Float, amount: Float, scene: (Canvas) -> Unit) {
        if (level <= 0f) return
        val shift = unit * MAX_SPLIT * level * amount
        ghost(canvas, red, shift, scene)
        ghost(canvas, cyan, -shift, scene)
    }

    // Shifted slices plus error blocks
    fun drawSlices(canvas: Canvas, width: Int, height: Int, color: Int, scene: (Canvas) -> Unit) {
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
            val left = (0.5f + offsets[i]).coerceIn(0f, 1f) * width
            canvas.drawRect(left, top, left + width * BLOCK_WIDTH, top + height * BLOCK_HEIGHT, block)
        }
    }

    private fun shuffle(amount: Float) {
        bands = (MIN_BANDS + amount * (MAX_BANDS - MIN_BANDS)).toInt()
        for (i in 0 until bands) {
            tops[i] = random.nextFloat()
            heights[i] = 0.01f + random.nextFloat() * 0.08f
            offsets[i] = (random.nextFloat() - 0.5f) * MAX_SHIFT * amount
        }
    }

    private fun ghost(canvas: Canvas, paint: Paint, dx: Float, scene: (Canvas) -> Unit) {
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
        const val SPLIT_ALPHA = 0.75f

        // Keeps one color channel only
        fun splitPaint(r: Float, g: Float, b: Float) = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        r, 0f, 0f, 0f, 0f,
                        0f, g, 0f, 0f, 0f,
                        0f, 0f, b, 0f, 0f,
                        0f, 0f, 0f, SPLIT_ALPHA, 0f,
                    ),
                ),
            )
        }
    }
}
