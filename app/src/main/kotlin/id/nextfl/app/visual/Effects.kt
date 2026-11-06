package id.nextfl.app.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
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
    private class Comet(val size: Float) {
        var x = 0f
        var y = 0f
        var heading = 0f
        var wobble = 0f
        var spin = 0f
        var angle = 0f
        var travelled = 0f
        var sinceEmit = 0f
        val trailX = FloatArray(TRAIL_POINTS)
        val trailY = FloatArray(TRAIL_POINTS)
        val trailAge = FloatArray(TRAIL_POINTS)
        val trailScale = FloatArray(TRAIL_POINTS)
        val trailPhase = FloatArray(TRAIL_POINTS)
        val trailReach = FloatArray(TRAIL_POINTS)
        var trailStart = 0
        var trailCount = 0
    }

    private val comets = ArrayList<Comet>()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }
    private val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
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

    // All smoke first, heads on top
    fun draw(canvas: Canvas, color: Int, unit: Float) {
        stroke.strokeWidth = unit * HEAD_STROKE
        border.strokeWidth = unit * (HEAD_STROKE + HEAD_BORDER * 2)
        for (comet in comets) drawPuffs(canvas, comet, color, unit)
        for (comet in comets) drawHead(canvas, comet)
    }

    private fun move(comet: Comet, speed: Float, dt: Float, unit: Float) {
        comet.wobble = (comet.wobble + (random.nextFloat() - 0.5f) * WOBBLE_JITTER * dt).coerceIn(-MAX_WOBBLE, MAX_WOBBLE)
        val direction = comet.heading + comet.wobble
        val step = speed * dt
        val fromX = comet.x
        val fromY = comet.y
        comet.x += cos(direction) * step
        comet.y += sin(direction) * step
        comet.angle += (comet.spin + boost * SPIN_BOOST) * dt
        ageTrail(comet, dt)
        emitTrail(comet, fromX, fromY, step, dt, unit)
        trimTrail(comet, unit)
    }

    // Interpolated puffs, never gaps
    private fun emitTrail(comet: Comet, fromX: Float, fromY: Float, step: Float, dt: Float, unit: Float) {
        val spacing = unit * TRAIL_SPACING
        var along = spacing - comet.travelled
        var emitted = false
        while (along <= step) {
            val f = along / step
            pushTrail(comet, fromX + (comet.x - fromX) * f, fromY + (comet.y - fromY) * f, dt * (1f - f))
            along += spacing
            emitted = true
        }
        comet.travelled = spacing - (along - step)
        comet.sinceEmit = if (emitted) 0f else comet.sinceEmit + dt
        if (comet.sinceEmit >= EMIT_SECONDS) {
            pushTrail(comet, comet.x, comet.y, 0f)
            comet.sinceEmit = 0f
            comet.travelled = 0f
        }
    }

    // Caps tail length behind head
    private fun trimTrail(comet: Comet, unit: Float) {
        val maxLength = unit * TRAIL_MAX_LENGTH
        if (maxLength <= 0f) {
            comet.trailCount = 0
            return
        }
        var reach = 0f
        var x = comet.x
        var y = comet.y
        var kept = 0
        for (i in comet.trailCount - 1 downTo 0) {
            val index = (comet.trailStart + i) % TRAIL_POINTS
            reach += hypot(comet.trailX[index] - x, comet.trailY[index] - y)
            if (reach > maxLength) break
            comet.trailReach[index] = reach / maxLength
            x = comet.trailX[index]
            y = comet.trailY[index]
            kept++
        }
        comet.trailStart = (comet.trailStart + comet.trailCount - kept) % TRAIL_POINTS
        comet.trailCount = kept
    }

    private fun isGone(comet: Comet, unit: Float): Boolean {
        val margin = unit * EDGE_MARGIN
        return comet.x < -margin || comet.y > height + margin
    }

    private fun pushTrail(comet: Comet, x: Float, y: Float, age: Float) {
        val index = (comet.trailStart + comet.trailCount) % TRAIL_POINTS
        comet.trailX[index] = x
        comet.trailY[index] = y
        comet.trailAge[index] = age
        comet.trailScale[index] = PUFF_MIN_SCALE + random.nextFloat() * PUFF_SCALE_SPREAD
        comet.trailPhase[index] = random.nextFloat() * TWO_PI
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
        puff.color = color
        for (i in 0 until comet.trailCount) {
            val index = (comet.trailStart + i) % TRAIL_POINTS
            val age = comet.trailAge[index]
            val life = (age / TRAIL_LIFE).coerceIn(0f, 1f)
            // Fades by age or length
            val t = max(life, comet.trailReach[index]).coerceIn(0f, 1f)
            val radius = comet.size * comet.trailScale[index] * PUFF_START * (1f - t * PUFF_TAPER)
            val driftX = sin(comet.trailPhase[index] + age * SMOKE_SWAY_RATE) * unit * SMOKE_SWAY * life
            val driftY = -unit * SMOKE_RISE * life
            puff.alpha = ((1f - t).pow(PUFF_FADE) * PUFF_ALPHA * 255).toInt().coerceIn(0, 255)
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
        canvas.drawPath(path, headFill)
        canvas.drawPath(path, border)
        canvas.drawPath(path, stroke)
    }

    // Enters from top or right edge
    private fun spawn(unit: Float, anywhere: Boolean): Comet {
        val comet = Comet(size = unit * (0.014f + random.nextFloat() * 0.012f))
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
        comet.angle = random.nextFloat() * TWO_PI
        comet.spin = (random.nextFloat() - 0.5f) * 1.2f
        return comet
    }

    private companion object {
        const val TRAIL_POINTS = 128
        const val TRAIL_LIFE = 0.8f
        const val TRAIL_MAX_LENGTH = 0.16f
        const val TRAIL_SPACING = 0.004f
        const val EMIT_SECONDS = 0.03f
        const val PUFF_START = 0.85f
        const val PUFF_TAPER = 0.85f
        const val PUFF_ALPHA = 0.3f
        const val PUFF_FADE = 1.6f
        const val PUFF_MIN_SCALE = 0.75f
        const val PUFF_SCALE_SPREAD = 0.5f
        const val CRUISE_SPEED = 0.05f
        const val BURST_SPEED = 3f
        const val ENERGY_SPEED = 0.08f
        const val BOOST_DECAY = 3.2f
        const val FALL_HEADING = 2.55f
        const val HEADING_SPREAD = 0.25f
        const val WOBBLE_JITTER = 1.2f
        const val MAX_WOBBLE = 0.15f
        const val SPIN_BOOST = 5f
        const val EDGE_MARGIN = 0.12f
        const val SMOKE_SWAY = 0.003f
        const val SMOKE_SWAY_RATE = 1.7f
        const val SMOKE_RISE = 0.004f
        const val HEAD_STROKE = 0.0035f
        const val HEAD_PULSE = 0.25f
        const val HEAD_BORDER = 0.004f
        const val THIRD_TURN = 2.0943952f
        const val TWO_PI = 6.2831855f
    }
}

/** Elastic water-like hills, mirrored. */
class BeatHills {
    private var target = 0f
    private var level = 0f
    private var velocity = 0f
    private var phase = 0f
    private var peak = 0f
    private var floor = 0f
    private var anchor = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Rises on beat, holds while bass lasts
    fun update(beat: Boolean, strength: Float, bass: Float, dt: Float) {
        floor += (bass - floor) * (1f - exp(-dt / FLOOR_SECONDS))
        if (beat) {
            peak = strength.pow(RESPONSE_CURVE)
            anchor = max(bass, floor + MIN_SPAN)
        }
        val sustain = ((bass - floor) / max(MIN_SPAN, anchor - floor)).coerceIn(0f, 1f)
        target = peak * sustain.pow(SUSTAIN_CURVE)
        if (sustain <= 0f) peak = 0f
        spring(dt)
        phase += dt * WAVE_SPEED
    }

    // Damped spring, sub-stepped for stability
    private fun spring(dt: Float) {
        var remaining = dt
        while (remaining > 0f) {
            val step = min(remaining, MAX_STEP_SECONDS)
            val rising = target > level
            val stiffness = if (rising) ATTACK_STIFFNESS else RELEASE_STIFFNESS
            val damping = if (rising) ATTACK_DAMPING else RELEASE_DAMPING
            velocity += ((target - level) * stiffness - velocity * damping) * step
            level += velocity * step
            if (level < 0f) {
                level = 0f
                velocity = max(velocity, 0f)
            }
            remaining -= step
        }
    }

    // Filled hills between circle and curve
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, sensitivity: Float) {
        if (level < MIN_LEVEL) return
        val lift = level * AMPLITUDE * sensitivity
        val halfWidth = HILL_HALF_WIDTH * (WIDTH_BASE + WIDTH_GROWTH * min(level, 1.2f))
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        for (j in 0 until POINTS) {
            val angle = -PI.toFloat() / 2 + j * 2 * PI.toFloat() / POINTS
            val hill = max(wave(angle, UPPER_LEFT, halfWidth), wave(angle, UPPER_RIGHT, halfWidth))
            val r = radius * (1f + hill * lift)
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        path.addCircle(cx, cy, radius, Path.Direction.CW)
        fill.color = color
        canvas.drawPath(path, fill)
    }

    // Bell with ripples running outward
    private fun wave(angle: Float, center: Float, halfWidth: Float): Float {
        var delta = angle - center
        while (delta > PI) delta -= 2 * PI.toFloat()
        while (delta < -PI) delta += 2 * PI.toFloat()
        val distance = abs(delta)
        if (distance >= halfWidth) return 0f
        val shape = (0.5f + 0.5f * cos(distance / halfWidth * PI.toFloat())).pow(HILL_POWER)
        val ripple = 1f + RIPPLE_DEPTH * sin(distance * WAVE_NUMBER - phase)
        return shape * ripple
    }

    private companion object {
        const val POINTS = 360
        const val AMPLITUDE = 0.42f
        const val HILL_HALF_WIDTH = 0.75f
        const val HILL_POWER = 1.4f
        const val WIDTH_BASE = 0.85f
        const val WIDTH_GROWTH = 0.25f
        const val ATTACK_STIFFNESS = 1600f
        const val ATTACK_DAMPING = 36f
        const val RELEASE_STIFFNESS = 480f
        const val RELEASE_DAMPING = 13f
        const val MAX_STEP_SECONDS = 1f / 240f
        const val RIPPLE_DEPTH = 0.18f
        const val WAVE_NUMBER = 12f
        const val WAVE_SPEED = 9f
        const val FLOOR_SECONDS = 1.2f
        const val MIN_SPAN = 0.04f
        const val SUSTAIN_CURVE = 0.7f
        const val RESPONSE_CURVE = 0.6f
        const val MIN_LEVEL = 0.005f
        val UPPER_RIGHT = (-PI / 4).toFloat()
        val UPPER_LEFT = (-3 * PI / 4).toFloat()
    }
}
