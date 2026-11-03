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

/** Triangle comets falling top-right to bottom-left. */
class CometField(private val random: Random = Random(11)) {
    private class Comet(val size: Float, val seed: Float) {
        var x = 0f
        var y = 0f
        var heading = 0f
        var wobble = 0f
        var spin = 0f
        var angle = 0f
        var travelled = 0f
        val trailX = FloatArray(TRAIL_POINTS)
        val trailY = FloatArray(TRAIL_POINTS)
        val trailAge = FloatArray(TRAIL_POINTS)
        var trailStart = 0
        var trailCount = 0
        var trailSerial = 0
    }

    private val comets = ArrayList<Comet>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val puff = Paint(Paint.ANTI_ALIAS_FLAG)
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
        while (comets.size < count) comets.add(spawn(unit, anywhere = true))
        while (comets.size > count) comets.removeAt(comets.lastIndex)
        if (beat) boost = max(boost, strength)
        boost *= exp(-dt * BOOST_DECAY)
        val speed = unit * (CRUISE_SPEED + boost * BURST_SPEED + energy * ENERGY_SPEED)
        for (index in comets.indices) {
            val comet = comets[index]
            move(comet, speed, dt, unit)
            if (isGone(comet, unit)) comets[index] = spawn(unit, anywhere = false)
        }
    }

    fun draw(canvas: Canvas, color: Int, unit: Float) {
        stroke.strokeWidth = unit * HEAD_STROKE
        for (comet in comets) {
            drawPuffs(canvas, comet, color, unit)
            drawHead(canvas, comet)
        }
    }

    private fun move(comet: Comet, speed: Float, dt: Float, unit: Float) {
        comet.wobble = (comet.wobble + (random.nextFloat() - 0.5f) * WOBBLE_JITTER * dt).coerceIn(-MAX_WOBBLE, MAX_WOBBLE)
        val direction = comet.heading + comet.wobble
        val step = speed * dt
        comet.x += cos(direction) * step
        comet.y += sin(direction) * step
        comet.angle += (comet.spin + boost * SPIN_BOOST) * dt
        ageTrail(comet, dt)
        comet.travelled += step
        if (comet.travelled >= unit * TRAIL_SPACING) {
            pushTrail(comet)
            comet.travelled = 0f
        }
    }

    private fun isGone(comet: Comet, unit: Float): Boolean {
        val margin = unit * EDGE_MARGIN
        return comet.x < -margin || comet.y > height + margin
    }

    private fun pushTrail(comet: Comet) {
        val index = (comet.trailStart + comet.trailCount) % TRAIL_POINTS
        comet.trailX[index] = comet.x
        comet.trailY[index] = comet.y
        comet.trailAge[index] = 0f
        comet.trailSerial++
        if (comet.trailCount < TRAIL_POINTS) comet.trailCount++ else comet.trailStart = (comet.trailStart + 1) % TRAIL_POINTS
    }

    private fun ageTrail(comet: Comet, dt: Float) {
        for (i in 0 until comet.trailCount) comet.trailAge[(comet.trailStart + i) % TRAIL_POINTS] += dt
        while (comet.trailCount > 0 && comet.trailAge[comet.trailStart] > TRAIL_LIFE) {
            comet.trailStart = (comet.trailStart + 1) % TRAIL_POINTS
            comet.trailCount--
        }
    }

    // Illustrated round smoke puffs
    private fun drawPuffs(canvas: Canvas, comet: Comet, color: Int, unit: Float) {
        val firstSerial = comet.trailSerial - comet.trailCount
        for (i in 0 until comet.trailCount) {
            if ((firstSerial + i) % PUFF_EVERY != 0) continue
            val index = (comet.trailStart + i) % TRAIL_POINTS
            val age = comet.trailAge[index]
            val t = (age / TRAIL_LIFE).coerceIn(0f, 1f)
            val radius = comet.size * (PUFF_START + t * PUFF_GROWTH)
            val driftX = sin(comet.seed + age * SMOKE_SWAY_RATE) * unit * SMOKE_SWAY * t
            val driftY = -unit * SMOKE_RISE * t
            puff.color = color
            puff.alpha = ((1f - t).pow(1.2f) * PUFF_ALPHA * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(comet.trailX[index] + driftX, comet.trailY[index] + driftY, radius, puff)
        }
    }

    private fun drawHead(canvas: Canvas, comet: Comet) {
        val pulse = 1f + boost * HEAD_PULSE
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

    // Enters from top or right edge
    private fun spawn(unit: Float, anywhere: Boolean): Comet {
        val comet = Comet(size = unit * (0.014f + random.nextFloat() * 0.012f), seed = random.nextFloat() * 10f)
        val margin = unit * EDGE_MARGIN * 0.5f
        when {
            anywhere -> {
                comet.x = random.nextFloat() * width
                comet.y = random.nextFloat() * height
            }
            random.nextBoolean() -> {
                comet.x = width * (0.35f + random.nextFloat() * 0.7f)
                comet.y = -margin
            }
            else -> {
                comet.x = width + margin
                comet.y = height * (random.nextFloat() * 0.6f - 0.1f)
            }
        }
        comet.heading = FALL_HEADING + (random.nextFloat() - 0.5f) * HEADING_SPREAD
        comet.angle = random.nextFloat() * 2 * PI.toFloat()
        comet.spin = (random.nextFloat() - 0.5f) * 1.2f
        return comet
    }

    private companion object {
        const val TRAIL_POINTS = 120
        const val TRAIL_LIFE = 1.3f
        const val TRAIL_SPACING = 0.0035f
        const val PUFF_EVERY = 3
        const val PUFF_START = 0.45f
        const val PUFF_GROWTH = 1.7f
        const val PUFF_ALPHA = 0.5f
        const val CRUISE_SPEED = 0.05f
        const val BURST_SPEED = 0.75f
        const val ENERGY_SPEED = 0.08f
        const val BOOST_DECAY = 3.2f
        const val FALL_HEADING = 2.55f
        const val HEADING_SPREAD = 0.25f
        const val WOBBLE_JITTER = 1.2f
        const val MAX_WOBBLE = 0.15f
        const val SPIN_BOOST = 5f
        const val EDGE_MARGIN = 0.12f
        const val SMOKE_SWAY = 0.012f
        const val SMOKE_SWAY_RATE = 1.7f
        const val SMOKE_RISE = 0.018f
        const val HEAD_STROKE = 0.0035f
        const val HEAD_PULSE = 0.25f
        const val HEAD_ALPHA = 235
        const val THIRD_TURN = 2.0943952f
    }
}

/** One smooth drum hill per upper corner. */
class SpectrumRing {
    private val baseline = FloatArray(LOW_BANDS)
    private var leftLevel = 0f
    private var rightLevel = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Transients above running average only
    fun update(bands: FloatArray, kick: Float, dt: Float) {
        val follow = 1f - exp(-dt / BASELINE_SECONDS)
        var low = 0f
        var high = 0f
        for (b in 0 until LOW_BANDS) {
            val level = bands[b]
            baseline[b] += (level - baseline[b]) * follow
            val transient = max(0f, level - baseline[b]) / max(MIN_HEADROOM, 1f - baseline[b])
            if (b < LOW_BANDS / 2) low = max(low, transient) else high = max(high, transient)
        }
        leftLevel = glide(leftLevel, min(1f, low * TRANSIENT_GAIN + kick * KICK_GAIN), dt)
        rightLevel = glide(rightLevel, min(1f, high * TRANSIENT_GAIN + kick * KICK_GAIN), dt)
    }

    // Filled hills between circle and curve
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, sensitivity: Float) {
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        for (j in 0 until POINTS) {
            val angle = -PI.toFloat() / 2 + j * 2 * PI.toFloat() / POINTS
            val hill = leftLevel * bell(angle, UPPER_LEFT) + rightLevel * bell(angle, UPPER_RIGHT)
            val r = radius * (1f + hill * AMPLITUDE * sensitivity)
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        path.addCircle(cx, cy, radius, Path.Direction.CW)
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun glide(current: Float, target: Float, dt: Float): Float {
        val seconds = if (target > current) ATTACK_SECONDS else RELEASE_SECONDS
        return current + (target - current) * (1f - exp(-dt / seconds))
    }

    // EQ-style bell, zero at edges
    private fun bell(angle: Float, center: Float): Float {
        var delta = angle - center
        while (delta > PI) delta -= 2 * PI.toFloat()
        while (delta < -PI) delta += 2 * PI.toFloat()
        if (abs(delta) >= HILL_HALF_WIDTH) return 0f
        val shape = 0.5f + 0.5f * cos(delta / HILL_HALF_WIDTH * PI.toFloat())
        return shape.pow(HILL_POWER)
    }

    private companion object {
        const val POINTS = 360
        const val LOW_BANDS = 20
        const val AMPLITUDE = 0.32f
        const val HILL_HALF_WIDTH = 0.75f
        const val HILL_POWER = 1.4f
        const val BASELINE_SECONDS = 0.8f
        const val ATTACK_SECONDS = 0.02f
        const val RELEASE_SECONDS = 0.16f
        const val MIN_HEADROOM = 0.08f
        const val TRANSIENT_GAIN = 2.2f
        const val KICK_GAIN = 0.35f
        val UPPER_RIGHT = (-PI / 4).toFloat()
        val UPPER_LEFT = (-3 * PI / 4).toFloat()
    }
}
