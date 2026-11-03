package id.nextfl.app.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
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

/** Triangle comets with smoky tails. */
class CometField(private val random: Random = Random(11)) {
    private class Comet(val size: Float, val seed: Float) {
        var x = 0f
        var y = 0f
        var heading = 0f
        var turn = 0f
        var spin = 0f
        var angle = 0f
        var travelled = 0f
        val trailX = FloatArray(TRAIL_POINTS)
        val trailY = FloatArray(TRAIL_POINTS)
        val trailAge = FloatArray(TRAIL_POINTS)
        var trailStart = 0
        var trailCount = 0
    }

    private val comets = ArrayList<Comet>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val path = Path()
    private var boost = 0f
    private var width = 0
    private var height = 0

    // Beat kicks speed, friction slows
    fun update(count: Int, dt: Float, beat: Boolean, strength: Float, energy: Float, width: Int, height: Int) {
        if (width != this.width || height != this.height) {
            comets.clear()
            this.width = width
            this.height = height
        }
        val unit = min(width, height).toFloat()
        while (comets.size < count) comets.add(spawn(unit))
        while (comets.size > count) comets.removeAt(comets.lastIndex)
        if (beat) boost = max(boost, strength)
        boost *= exp(-dt * BOOST_DECAY)
        val speed = unit * (CRUISE_SPEED + boost * BURST_SPEED + energy * ENERGY_SPEED)
        for (comet in comets) move(comet, speed, dt, unit)
    }

    fun draw(canvas: Canvas, sprite: GlowSprite, color: Int, unit: Float) {
        stroke.strokeWidth = unit * HEAD_STROKE
        for (comet in comets) {
            drawSmoke(canvas, sprite, comet, color, unit)
            drawHead(canvas, sprite, comet, color)
        }
    }

    private fun move(comet: Comet, speed: Float, dt: Float, unit: Float) {
        comet.turn = (comet.turn + (random.nextFloat() - 0.5f) * TURN_JITTER * dt).coerceIn(-MAX_TURN, MAX_TURN)
        comet.heading += comet.turn * dt
        val step = speed * dt
        comet.x += cos(comet.heading) * step
        comet.y += sin(comet.heading) * step
        comet.angle += (comet.spin + boost * SPIN_BOOST) * dt
        ageTrail(comet, dt)
        comet.travelled += step
        if (comet.travelled >= unit * TRAIL_SPACING) {
            pushTrail(comet)
            comet.travelled = 0f
        }
        wrap(comet, unit)
    }

    private fun wrap(comet: Comet, unit: Float) {
        val margin = unit * EDGE_MARGIN
        val wrapped = when {
            comet.x < -margin -> { comet.x = width + margin; true }
            comet.x > width + margin -> { comet.x = -margin; true }
            comet.y < -margin -> { comet.y = height + margin; true }
            comet.y > height + margin -> { comet.y = -margin; true }
            else -> false
        }
        if (wrapped) comet.trailCount = 0
    }

    private fun pushTrail(comet: Comet) {
        val index = (comet.trailStart + comet.trailCount) % TRAIL_POINTS
        comet.trailX[index] = comet.x
        comet.trailY[index] = comet.y
        comet.trailAge[index] = 0f
        if (comet.trailCount < TRAIL_POINTS) comet.trailCount++ else comet.trailStart = (comet.trailStart + 1) % TRAIL_POINTS
    }

    private fun ageTrail(comet: Comet, dt: Float) {
        for (i in 0 until comet.trailCount) comet.trailAge[(comet.trailStart + i) % TRAIL_POINTS] += dt
        while (comet.trailCount > 0 && comet.trailAge[comet.trailStart] > TRAIL_LIFE) {
            comet.trailStart = (comet.trailStart + 1) % TRAIL_POINTS
            comet.trailCount--
        }
    }

    // Puffs widen and fade like smoke
    private fun drawSmoke(canvas: Canvas, sprite: GlowSprite, comet: Comet, color: Int, unit: Float) {
        for (i in 0 until comet.trailCount) {
            val index = (comet.trailStart + i) % TRAIL_POINTS
            val age = comet.trailAge[index]
            val t = (age / TRAIL_LIFE).coerceIn(0f, 1f)
            val radius = comet.size * (SMOKE_START + t * SMOKE_GROWTH)
            val alpha = (1f - t).pow(1.6f) * SMOKE_ALPHA
            val driftX = sin(comet.seed + age * SMOKE_SWAY_RATE) * unit * SMOKE_SWAY * t
            val driftY = -unit * SMOKE_RISE * t
            sprite.draw(canvas, comet.trailX[index] + driftX, comet.trailY[index] + driftY, radius, color, alpha)
        }
    }

    private fun drawHead(canvas: Canvas, sprite: GlowSprite, comet: Comet, color: Int) {
        val pulse = 1f + boost * HEAD_PULSE
        sprite.draw(canvas, comet.x, comet.y, comet.size * HEAD_GLOW * pulse, color, HEAD_GLOW_ALPHA + boost * 0.3f)
        path.reset()
        for (k in 0..2) {
            val a = comet.angle + k * THIRD_TURN
            val px = comet.x + cos(a) * comet.size * pulse
            val py = comet.y + sin(a) * comet.size * pulse
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        stroke.alpha = HEAD_ALPHA
        canvas.drawPath(path, stroke)
    }

    private fun spawn(unit: Float): Comet {
        val comet = Comet(size = unit * (0.014f + random.nextFloat() * 0.012f), seed = random.nextFloat() * 10f)
        comet.x = random.nextFloat() * width
        comet.y = random.nextFloat() * height
        comet.heading = random.nextFloat() * 2 * PI.toFloat()
        comet.angle = random.nextFloat() * 2 * PI.toFloat()
        comet.spin = (random.nextFloat() - 0.5f) * 1.2f
        return comet
    }

    private companion object {
        const val TRAIL_POINTS = 120
        const val TRAIL_LIFE = 1.3f
        const val TRAIL_SPACING = 0.0035f
        const val CRUISE_SPEED = 0.035f
        const val BURST_SPEED = 0.75f
        const val ENERGY_SPEED = 0.08f
        const val BOOST_DECAY = 3.2f
        const val TURN_JITTER = 3f
        const val MAX_TURN = 0.9f
        const val SPIN_BOOST = 5f
        const val EDGE_MARGIN = 0.12f
        const val SMOKE_START = 1.1f
        const val SMOKE_GROWTH = 3.4f
        const val SMOKE_ALPHA = 0.16f
        const val SMOKE_SWAY = 0.012f
        const val SMOKE_SWAY_RATE = 1.7f
        const val SMOKE_RISE = 0.018f
        const val HEAD_STROKE = 0.0035f
        const val HEAD_PULSE = 0.25f
        const val HEAD_GLOW = 1.8f
        const val HEAD_GLOW_ALPHA = 0.3f
        const val HEAD_ALPHA = 235
        const val THIRD_TURN = 2.0943952f
    }
}

/** Drum-driven spikes at upper corners. */
class SpectrumRing {
    private val baseline = FloatArray(LOW_BANDS)
    private val drive = FloatArray(LOW_BANDS)
    private val radii = FloatArray(POINTS)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        strokeMiter = 8f
    }
    private val path = Path()

    // Transients above running average only
    fun update(bands: FloatArray, kick: Float, dt: Float) {
        val follow = 1f - exp(-dt / BASELINE_SECONDS)
        val release = exp(-dt * RELEASE_PER_SECOND)
        for (b in 0 until LOW_BANDS) {
            val level = bands[b]
            baseline[b] += (level - baseline[b]) * follow
            val transient = max(0f, level - baseline[b]) / max(MIN_HEADROOM, 1f - baseline[b])
            val target = min(1f, transient * TRANSIENT_GAIN + kick * KICK_GAIN)
            drive[b] = max(target, drive[b] * release)
        }
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, sensitivity: Float) {
        for (j in 0 until POINTS) radii[j] = radius * (BASE_SCALE + spike(angleOf(j)) * AMPLITUDE * sensitivity)
        path.reset()
        for (j in 0 until POINTS) {
            val angle = angleOf(j)
            val x = cx + cos(angle) * radii[j]
            val y = cy + sin(angle) * radii[j]
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = color
        fill.alpha = 30
        canvas.drawPath(path, fill)
        stroke.color = color
        stroke.alpha = 245
        stroke.strokeWidth = radius * 0.02f
        canvas.drawPath(path, stroke)
    }

    private fun angleOf(index: Int): Float = -PI.toFloat() / 2 + index * 2 * PI.toFloat() / POINTS

    // Sharp per-band peaks inside each lobe
    private fun spike(angle: Float): Float {
        val right = lobe(angle, UPPER_RIGHT)
        val left = lobe(angle, UPPER_LEFT)
        val weight = max(right.first, left.first)
        if (weight <= 0f) return 0f
        val offset = if (right.first >= left.first) right.second else -left.second
        val cell = (offset + 1f) / 2f * SPIKES
        val spike = cell.toInt().coerceIn(0, SPIKES - 1)
        val tip = 1f - abs(cell - spike - 0.5f) * 2f
        val level = max(drive[spike * 2], drive[spike * 2 + 1])
        return weight * level.pow(SHARPNESS) * tip.pow(TIP_POWER)
    }

    // Raised-cosine window and normalized offset
    private fun lobe(angle: Float, center: Float): Pair<Float, Float> {
        var delta = angle - center
        while (delta > PI) delta -= 2 * PI.toFloat()
        while (delta < -PI) delta += 2 * PI.toFloat()
        if (abs(delta) >= LOBE_HALF_WIDTH) return 0f to 0f
        val offset = delta / LOBE_HALF_WIDTH
        return (0.5f + 0.5f * cos(offset * PI.toFloat())) to offset
    }

    private companion object {
        const val POINTS = 360
        const val LOW_BANDS = 20
        const val SPIKES = LOW_BANDS / 2
        const val BASE_SCALE = 1.03f
        const val AMPLITUDE = 0.55f
        const val SHARPNESS = 1.4f
        const val TIP_POWER = 2.2f
        const val BASELINE_SECONDS = 0.8f
        const val RELEASE_PER_SECOND = 9f
        const val MIN_HEADROOM = 0.08f
        const val TRANSIENT_GAIN = 2.2f
        const val KICK_GAIN = 0.35f
        const val LOBE_HALF_WIDTH = 0.6f
        val UPPER_RIGHT = (-PI / 4).toFloat()
        val UPPER_LEFT = (-3 * PI / 4).toFloat()
    }
}
