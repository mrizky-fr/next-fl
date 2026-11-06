package id.nextfl.core.dsp

import id.nextfl.core.model.DjSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Deck FX: filter, echo, flanger, crush. */
class DjEffects(private val sampleRate: Int) {
    private val filter = SweepFilter(sampleRate)
    private val echo = BeatEcho(sampleRate)
    private val flanger = Flanger(sampleRate)
    private val crusher = Crusher(sampleRate)

    fun process(settings: DjSettings, bpm: Double, left: FloatArray, right: FloatArray, frames: Int) {
        filter.process(settings.filter, left, right, frames)
        echo.process(settings.echo, bpm, left, right, frames)
        flanger.process(settings.flanger, left, right, frames)
        crusher.process(settings.crush, left, right, frames)
    }

    companion object {
        const val OFF = 0.01f
    }
}

private const val DENORMAL = 1e-20f
private const val SILENT_MIX = 1e-3f
private const val MIX_GLIDE_SECONDS = 0.005

// Per-step one-pole glide factor
private fun glideRate(seconds: Double, sampleRate: Int, stepFrames: Int = 1): Float =
    (1 - exp(-stepFrames / (seconds * sampleRate))).toFloat()

private fun flush(value: Float): Float = if (abs(value) < DENORMAL) 0f else value

/** Left lowpass, right highpass. */
private class SweepFilter(private val sampleRate: Int) {
    private val glide = glideRate(GLIDE_SECONDS, sampleRate, SUB_BLOCK)
    private val fade = glideRate(FADE_SECONDS, sampleRate, SUB_BLOCK)
    private val channelL = SvfChannel()
    private val channelR = SvfChannel()
    private var amount = 0f
    private var side = IDLE
    private var mix = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var damping = 0f

    // Dry/wet fade; side flip refades
    fun process(target: Float, left: FloatArray, right: FloatArray, frames: Int) {
        val wanted = if (abs(target) < DjEffects.OFF) 0f else target
        val wantedSide = sideOf(wanted)
        if (side == IDLE) {
            if (wantedSide == IDLE) return
            begin(wantedSide)
        }
        var start = 0
        while (start < frames) {
            val end = min(frames, start + SUB_BLOCK)
            val sameSide = wantedSide == side
            val from = mix
            mix += ((if (sameSide) 1f else 0f) - mix) * fade
            if (sameSide) amount += (wanted - amount) * glide
            design(amount, side)
            render(left, right, start, end, from)
            start = end
            if (!sameSide && mix < MIX_FLOOR) {
                if (wantedSide == IDLE) {
                    side = IDLE
                    return
                }
                begin(wantedSide)
            }
        }
        channelL.flushState()
        channelR.flushState()
    }

    private fun begin(newSide: Int) {
        side = newSide
        amount = 0f
        mix = 0f
        channelL.reset()
        channelR.reset()
    }

    private fun render(left: FloatArray, right: FloatArray, start: Int, end: Int, from: Float) {
        val highPass = side == HIGH
        val step = (mix - from) / (end - start)
        var weight = from
        for (i in start until end) {
            weight += step
            val dryL = left[i]
            val dryR = right[i]
            left[i] = dryL + (channelL.tick(dryL, a1, a2, a3, damping, highPass) - dryL) * weight
            right[i] = dryR + (channelR.tick(dryR, a1, a2, a3, damping, highPass) - dryR) * weight
        }
    }

    private fun sideOf(value: Float) = if (value > 0f) HIGH else if (value < 0f) LOW else IDLE

    // Simper SVF, safe under modulation
    private fun design(value: Float, filterSide: Int) {
        val depth = abs(value).toDouble()
        val frequency = if (filterSide == LOW) {
            LOW_TOP * (LOW_BOTTOM / LOW_TOP).pow(depth)
        } else {
            HIGH_BOTTOM * (HIGH_TOP / HIGH_BOTTOM).pow(depth)
        }
        val g = tan(PI * min(frequency, sampleRate * MAX_RATIO) / sampleRate)
        val k = 1.0 / (BASE_Q + RESONANCE * depth)
        val c1 = 1.0 / (1.0 + g * (g + k))
        a1 = c1.toFloat()
        a2 = (g * c1).toFloat()
        a3 = (g * g * c1).toFloat()
        damping = k.toFloat()
    }

    private companion object {
        const val LOW_TOP = 20_000.0
        const val LOW_BOTTOM = 150.0
        const val HIGH_BOTTOM = 20.0
        const val HIGH_TOP = 5_000.0
        const val BASE_Q = 0.707
        const val RESONANCE = 0.6
        const val MAX_RATIO = 0.45
        const val SUB_BLOCK = 16
        const val GLIDE_SECONDS = 0.012
        const val FADE_SECONDS = 0.004
        const val MIX_FLOOR = 1e-3f
        const val IDLE = 0
        const val LOW = -1
        const val HIGH = 1
    }
}

/** Trapezoidal state variable filter channel. */
private class SvfChannel {
    private var ic1 = 0f
    private var ic2 = 0f

    fun tick(input: Float, a1: Float, a2: Float, a3: Float, damping: Float, highPass: Boolean): Float {
        val v3 = input - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        return if (highPass) input - damping * v1 - v2 else v2
    }

    fun reset() {
        ic1 = 0f
        ic2 = 0f
    }

    fun flushState() {
        ic1 = flush(ic1)
        ic2 = flush(ic2)
    }
}

/** Half-beat ping-pong; tail rings out. */
private class BeatEcho(private val sampleRate: Int) {
    private val bufferL = FloatArray(sampleRate * MAX_SECONDS)
    private val bufferR = FloatArray(sampleRate * MAX_SECONDS)
    private val sendGlide = glideRate(MIX_GLIDE_SECONDS, sampleRate)
    private val delayGlide = glideRate(DELAY_GLIDE_SECONDS, sampleRate)
    private var write = 0
    private var dampL = 0f
    private var dampR = 0f
    private var send = 0f
    private var delay = -1.0
    private var silentFrames = SILENT_CAP

    fun process(target: Float, bpm: Double, left: FloatArray, right: FloatArray, frames: Int) {
        val size = bufferL.size
        val beatFrames = if (bpm > 0) sampleRate * BEATS * 60.0 / bpm else size.toDouble()
        val wanted = beatFrames.coerceIn(1.0, size - 2.0)
        val on = target >= DjEffects.OFF
        silentFrames = if (on) 0 else minOf(silentFrames + frames, SILENT_CAP)
        if (delay < 0 || silentFrames > wanted * TAIL_REPEATS) delay = wanted
        if (silentFrames > wanted * TAIL_REPEATS) {
            send = 0f
            return
        }
        val input = if (on) target else 0f
        for (i in 0 until frames) {
            send += (input - send) * sendGlide
            delay += (wanted - delay) * delayGlide
            val delayedL = readBehind(bufferL, delay)
            val delayedR = readBehind(bufferR, delay)
            dampL += (delayedL - dampL) * DAMPING
            dampR += (delayedR - dampR) * DAMPING
            bufferL[write] = flush(left[i] * send + dampR * FEEDBACK)
            bufferR[write] = flush(right[i] * send + dampL * FEEDBACK)
            left[i] += delayedL * WET
            right[i] += delayedR * WET
            write = if (write + 1 == size) 0 else write + 1
        }
        dampL = flush(dampL)
        dampR = flush(dampR)
    }

    // Fractional read glides smoothly
    private fun readBehind(buffer: FloatArray, frames: Double): Float {
        val size = buffer.size
        var position = write - frames
        if (position < 0) position += size
        val base = position.toInt()
        val t = (position - base).toFloat()
        val a = buffer[base]
        val b = buffer[if (base + 1 == size) 0 else base + 1]
        return a + (b - a) * t
    }

    private companion object {
        const val MAX_SECONDS = 2
        const val BEATS = 0.5
        const val FEEDBACK = 0.55f
        const val DAMPING = 0.6f
        const val WET = 0.8f
        const val TAIL_REPEATS = 14
        const val SILENT_CAP = 1 shl 30
        const val DELAY_GLIDE_SECONDS = 0.08
    }
}

/** Swept short delay with feedback. */
private class Flanger(private val sampleRate: Int) {
    private val size = (sampleRate * MAX_DELAY_SECONDS).roundToInt() + 4
    private val bufferL = FloatArray(size)
    private val bufferR = FloatArray(size)
    private val glide = glideRate(MIX_GLIDE_SECONDS, sampleRate)
    private var write = 0
    private var phase = 0.0
    private var mix = 0f

    fun process(target: Float, left: FloatArray, right: FloatArray, frames: Int) {
        val wanted = if (target >= DjEffects.OFF) target else 0f
        if (wanted == 0f && mix < SILENT_MIX) {
            mix = 0f
            record(left, right, frames)
            return
        }
        val step = 2 * PI * RATE_HZ / sampleRate
        for (i in 0 until frames) {
            mix += (wanted - mix) * glide
            val feedback = FEEDBACK * mix
            val wet = WET * mix
            val outL = read(bufferL, delayFrames(sin(phase)))
            val outR = read(bufferR, delayFrames(sin(phase + PI / 2)))
            bufferL[write] = flush(left[i] + outL * feedback)
            bufferR[write] = flush(right[i] + outR * feedback)
            left[i] = left[i] * (1 - wet * DRY_DUCK) + outL * wet
            right[i] = right[i] * (1 - wet * DRY_DUCK) + outR * wet
            write = if (write + 1 == size) 0 else write + 1
            phase += step
        }
        if (phase > 2 * PI) phase -= 2 * PI
    }

    // Keeps line warm: click-free start
    private fun record(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            bufferL[write] = left[i]
            bufferR[write] = right[i]
            write = if (write + 1 == size) 0 else write + 1
        }
    }

    private fun delayFrames(lfo: Double) = sampleRate * (MIN_DELAY_SECONDS + (MAX_DELAY_SECONDS - MIN_DELAY_SECONDS) * (lfo + 1) / 2)

    private fun read(buffer: FloatArray, delay: Double): Float {
        val position = write - delay
        val base = floor(position).toInt()
        val t = (position - base).toFloat()
        val a = buffer[(base % size + size) % size]
        val b = buffer[((base + 1) % size + size) % size]
        return a + (b - a) * t
    }

    private companion object {
        const val MIN_DELAY_SECONDS = 0.0005
        const val MAX_DELAY_SECONDS = 0.005
        const val RATE_HZ = 0.25
        const val FEEDBACK = 0.6f
        const val WET = 0.7f
        const val DRY_DUCK = 0.5f
    }
}

/** Bit depth and rate reduction. */
private class Crusher(sampleRate: Int) {
    private val glide = glideRate(MIX_GLIDE_SECONDS, sampleRate)
    private var holdL = 0f
    private var holdR = 0f
    private var counter = 0
    private var amount = 0f
    private var mix = 0f

    fun process(target: Float, left: FloatArray, right: FloatArray, frames: Int) {
        val on = target >= DjEffects.OFF
        if (!on && mix < SILENT_MIX) {
            mix = 0f
            return
        }
        if (on) amount = target
        val wanted = if (on) 1f else 0f
        val levels = 2f.pow(MAX_BITS - (MAX_BITS - MIN_BITS) * amount)
        val hold = max(1, 1 + Math.round(MAX_HOLD * amount))
        for (i in 0 until frames) {
            if (counter <= 0) {
                holdL = Math.round(left[i] * levels) / levels
                holdR = Math.round(right[i] * levels) / levels
                counter = hold
            }
            counter--
            mix += (wanted - mix) * glide
            left[i] += (holdL - left[i]) * mix
            right[i] += (holdR - right[i]) * mix
        }
    }

    private companion object {
        const val MAX_BITS = 12f
        const val MIN_BITS = 3f
        const val MAX_HOLD = 9
    }
}
