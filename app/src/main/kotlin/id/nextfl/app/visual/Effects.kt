package id.nextfl.app.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Drifting glowing dust. */
class ParticleField(private val random: Random = Random(7)) {
    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val size: Float, val depth: Float, val phase: Float, val tinted: Boolean)

    private val particles = ArrayList<Particle>()
    private var time = 0f

    fun update(count: Int, dt: Float, energy: Float, kick: Float) {
        while (particles.size < count) particles.add(spawn())
        while (particles.size > count) particles.removeAt(particles.lastIndex)
        time += dt
        val boost = 1f + energy * ENERGY_BOOST + kick * KICK_BOOST
        for (p in particles) {
            p.x = wrap(p.x + p.vx * p.depth * boost * dt)
            p.y = wrap(p.y + p.vy * p.depth * boost * dt)
        }
    }

    fun draw(canvas: Canvas, sprite: GlowSprite, width: Int, height: Int, color: Int, kick: Float) {
        val unit = min(width, height).toFloat()
        for (p in particles) {
            val twinkle = 0.55f + 0.45f * sin(time * 2.2f + p.phase)
            val radius = p.size * unit * (1f + kick * 0.6f) * p.depth
            val alpha = (0.25f + 0.6f * twinkle) * p.depth
            sprite.draw(canvas, p.x * width, p.y * height, radius * 3f, if (p.tinted) color else Color.WHITE, alpha * 0.55f)
            sprite.draw(canvas, p.x * width, p.y * height, radius, Color.WHITE, alpha)
        }
    }

    private fun spawn(): Particle {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val speed = 0.004f + random.nextFloat() * 0.02f
        return Particle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            vx = cos(angle) * speed,
            vy = sin(angle) * speed - 0.006f,
            size = 0.002f + random.nextFloat() * 0.005f,
            depth = 0.45f + random.nextFloat() * 0.55f,
            phase = random.nextFloat() * 10f,
            tinted = random.nextFloat() < 0.65f,
        )
    }

    private fun wrap(value: Float) = if (value < -0.05f) value + 1.1f else if (value > 1.05f) value - 1.1f else value

    private companion object {
        const val ENERGY_BOOST = 3f
        const val KICK_BOOST = 5f
    }
}

/** Beat-fired light streaks. */
class StreakField(private val random: Random = Random(11)) {
    private class Streak(var x: Float, var y: Float, val dx: Float, val dy: Float, val speed: Float, var life: Float, val length: Float, val head: Float)
    private class Shard(var x: Float, var y: Float, val dx: Float, val dy: Float, var angle: Float, val spin: Float, var life: Float, val size: Float)

    private val streaks = ArrayList<Streak>()
    private val shards = ArrayList<Shard>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    // Mirrored pair through the center
    fun spawn(cx: Float, cy: Float, radius: Float, unit: Float, strength: Float) {
        if (streaks.size > MAX_STREAKS) return
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val tilt = (random.nextFloat() - 0.5f) * 0.6f
        for (side in 0..1) {
            val a = angle + side * PI.toFloat()
            val dir = a + tilt
            val start = radius * (1.25f + random.nextFloat() * 0.4f)
            val x = cx + cos(a) * start
            val y = cy + sin(a) * start
            val speed = unit * (1.1f + strength * 1.4f)
            streaks.add(Streak(x, y, cos(dir), sin(dir), speed, STREAK_LIFE, unit * (0.28f + strength * 0.25f), unit * (0.007f + strength * 0.006f)))
            if (random.nextFloat() < 0.6f) {
                shards.add(Shard(x, y, cos(dir) * unit * 0.25f, sin(dir) * unit * 0.25f, random.nextFloat() * 6f, (random.nextFloat() - 0.5f) * 4f, SHARD_LIFE, unit * 0.018f))
            }
        }
    }

    fun update(dt: Float) {
        streaks.forEach { it.x += it.dx * it.speed * dt; it.y += it.dy * it.speed * dt; it.life -= dt }
        shards.forEach { it.x += it.dx * dt; it.y += it.dy * dt; it.angle += it.spin * dt; it.life -= dt }
        streaks.removeAll { it.life <= 0f }
        shards.removeAll { it.life <= 0f }
    }

    fun draw(canvas: Canvas, sprite: GlowSprite, color: Int, unit: Float) {
        for (streak in streaks) {
            val fade = (streak.life / STREAK_LIFE).pow(0.6f)
            for (i in 0 until DOTS) {
                val t = i / (DOTS - 1f)
                val back = streak.length * t.pow(1.35f)
                val radius = streak.head * (1f - t * 0.85f)
                val alpha = (1f - t).pow(1.2f) * fade
                sprite.draw(canvas, streak.x - streak.dx * back, streak.y - streak.dy * back, radius * 2.6f, color, alpha * 0.5f)
                sprite.draw(canvas, streak.x - streak.dx * back, streak.y - streak.dy * back, radius, Color.WHITE, alpha)
            }
        }
        stroke.strokeWidth = unit * 0.0035f
        for (shard in shards) {
            stroke.color = Color.WHITE
            stroke.alpha = (255 * (shard.life / SHARD_LIFE)).toInt().coerceIn(0, 255)
            path.reset()
            for (k in 0..2) {
                val a = shard.angle + k * 2.0944f
                val px = shard.x + cos(a) * shard.size
                val py = shard.y + sin(a) * shard.size
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, stroke)
        }
    }

    private companion object {
        const val DOTS = 22
        const val MAX_STREAKS = 12
        const val STREAK_LIFE = 0.9f
        const val SHARD_LIFE = 1.4f
    }
}

/** Mirrored spectrum ring blob. */
class SpectrumRing {
    private val smoothed = FloatArray(BANDS_USED)
    private val radii = FloatArray(POINTS)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun update(bands: FloatArray, dt: Float) {
        val release = exp(-dt * RELEASE_PER_SECOND)
        for (i in 0 until BANDS_USED) smoothed[i] = max(bands[i], smoothed[i] * release)
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, sensitivity: Float, rotation: Float) {
        val half = POINTS / 2
        for (j in 0 until POINTS) {
            val k = if (j < half) j else POINTS - 1 - j
            val band = (k * BANDS_USED / half).coerceIn(0, BANDS_USED - 1)
            val v = neighbourhood(band)
            radii[j] = radius * (1.03f + v.pow(1.7f) * AMPLITUDE * sensitivity)
        }
        buildPath(cx, cy, rotation)
        fill.color = color
        fill.alpha = 34
        canvas.drawPath(path, fill)
        stroke.color = color
        stroke.alpha = 50
        stroke.strokeWidth = radius * 0.06f
        canvas.drawPath(path, stroke)
        stroke.alpha = 240
        stroke.strokeWidth = radius * 0.022f
        canvas.drawPath(path, stroke)
    }

    private fun neighbourhood(band: Int): Float {
        var sum = 0f
        var weight = 0f
        for (offset in -2..2) {
            val index = band + offset
            if (index !in 0 until BANDS_USED) continue
            val w = 3f - kotlin.math.abs(offset)
            sum += smoothed[index] * w
            weight += w
        }
        return sum / weight
    }

    private fun buildPath(cx: Float, cy: Float, rotation: Float) {
        path.reset()
        fun point(j: Int): Pair<Float, Float> {
            val angle = -PI.toFloat() / 2 + rotation + j * 2 * PI.toFloat() / POINTS
            val r = radii[(j + POINTS) % POINTS]
            return cx + cos(angle) * r to cy + sin(angle) * r
        }
        val (sx, sy) = point(0)
        val (nx, ny) = point(1)
        path.moveTo((sx + nx) / 2, (sy + ny) / 2)
        for (j in 1..POINTS) {
            val (x, y) = point(j)
            val (x2, y2) = point(j + 1)
            path.quadTo(x, y, (x + x2) / 2, (y + y2) / 2)
        }
        path.close()
    }

    private companion object {
        const val POINTS = 96
        const val BANDS_USED = 44
        const val AMPLITUDE = 0.42f
        const val RELEASE_PER_SECOND = 5f
    }
}
