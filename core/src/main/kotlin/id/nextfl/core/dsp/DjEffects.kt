package id.nextfl.core.dsp

import id.nextfl.core.model.DjSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Deck FX: filter, echo, flanger, crush. */
class DjEffects(private val sampleRate: Int) {
    private val filter = SweepFilter(sampleRate)
    private val echo = BeatEcho(sampleRate)
    private val flanger = Flanger(sampleRate)
    private val crusher = Crusher()

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

/** Left lowpass, right highpass. */
private class SweepFilter(private val sampleRate: Int) {
    private val biquad = StereoBiquad()
    private var lastAmount = 0f
    private var active = false

    fun process(amount: Float, left: FloatArray, right: FloatArray, frames: Int) {
        if (abs(amount) < DjEffects.OFF) {
            active = false
            return
        }
        // Fresh state avoids stale click
        if (!active || amount * lastAmount <= 0f) biquad.reset()
        if (!active || amount != lastAmount) biquad.set(design(amount))
        active = true
        lastAmount = amount
        biquad.process(left, right, 0, frames)
    }

    private fun design(amount: Float): BiquadCoefficients {
        val depth = abs(amount).toDouble()
        val q = BASE_Q + RESONANCE * depth
        return if (amount < 0) {
            BiquadCoefficients.design(FilterType.LOW_PASS, LOW_TOP * (LOW_BOTTOM / LOW_TOP).pow(depth), sampleRate, q)
        } else {
            BiquadCoefficients.design(FilterType.HIGH_PASS, HIGH_BOTTOM * (HIGH_TOP / HIGH_BOTTOM).pow(depth), sampleRate, q)
        }
    }

    private companion object {
        const val LOW_TOP = 20_000.0
        const val LOW_BOTTOM = 150.0
        const val HIGH_BOTTOM = 20.0
        const val HIGH_TOP = 5_000.0
        const val BASE_Q = 0.707
        const val RESONANCE = 0.6
    }
}

/** Half-beat ping-pong; tail rings out. */
private class BeatEcho(private val sampleRate: Int) {
    private val bufferL = FloatArray(sampleRate * MAX_SECONDS)
    private val bufferR = FloatArray(sampleRate * MAX_SECONDS)
    private var write = 0
    private var dampL = 0f
    private var dampR = 0f
    private var silentFrames = SILENT_CAP

    fun process(send: Float, bpm: Double, left: FloatArray, right: FloatArray, frames: Int) {
        val delay = (sampleRate * BEATS * 60.0 / bpm).roundToInt().coerceIn(1, bufferL.size - 1)
        silentFrames = if (send >= DjEffects.OFF) 0 else minOf(silentFrames + frames, SILENT_CAP)
        if (silentFrames > delay * TAIL_REPEATS) return
        val input = if (send >= DjEffects.OFF) send else 0f
        val size = bufferL.size
        for (i in 0 until frames) {
            val read = (write - delay + size) % size
            val delayedL = bufferL[read]
            val delayedR = bufferR[read]
            dampL += (delayedL - dampL) * DAMPING
            dampR += (delayedR - dampR) * DAMPING
            bufferL[write] = left[i] * input + dampR * FEEDBACK
            bufferR[write] = right[i] * input + dampL * FEEDBACK
            left[i] += delayedL * WET
            right[i] += delayedR * WET
            write = (write + 1) % size
        }
    }

    private companion object {
        const val MAX_SECONDS = 2
        const val BEATS = 0.5
        const val FEEDBACK = 0.55f
        const val DAMPING = 0.6f
        const val WET = 0.8f
        const val TAIL_REPEATS = 14
        const val SILENT_CAP = 1 shl 30
    }
}

/** Swept short delay with feedback. */
private class Flanger(private val sampleRate: Int) {
    private val size = (sampleRate * MAX_DELAY_SECONDS).roundToInt() + 4
    private val bufferL = FloatArray(size)
    private val bufferR = FloatArray(size)
    private var write = 0
    private var phase = 0.0
    private var active = false

    fun process(amount: Float, left: FloatArray, right: FloatArray, frames: Int) {
        if (amount < DjEffects.OFF) {
            active = false
            return
        }
        if (!active) {
            bufferL.fill(0f)
            bufferR.fill(0f)
        }
        active = true
        val step = 2 * PI * RATE_HZ / sampleRate
        val feedback = FEEDBACK * amount
        val wet = WET * amount
        for (i in 0 until frames) {
            val delayL = delayFrames(sin(phase))
            val delayR = delayFrames(sin(phase + PI / 2))
            val outL = read(bufferL, delayL)
            val outR = read(bufferR, delayR)
            bufferL[write] = left[i] + outL * feedback
            bufferR[write] = right[i] + outR * feedback
            left[i] = left[i] * (1 - wet * 0.5f) + outL * wet
            right[i] = right[i] * (1 - wet * 0.5f) + outR * wet
            write = (write + 1) % size
            phase += step
        }
        if (phase > 2 * PI) phase -= 2 * PI
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
    }
}

/** Bit depth and rate reduction. */
private class Crusher {
    private var holdL = 0f
    private var holdR = 0f
    private var counter = 0

    fun process(amount: Float, left: FloatArray, right: FloatArray, frames: Int) {
        if (amount < DjEffects.OFF) return
        val levels = 2f.pow(MAX_BITS - (MAX_BITS - MIN_BITS) * amount)
        val hold = max(1, 1 + (MAX_HOLD * amount).roundToInt())
        for (i in 0 until frames) {
            if (counter <= 0) {
                holdL = (left[i] * levels).roundToInt() / levels
                holdR = (right[i] * levels).roundToInt() / levels
                counter = hold
            }
            counter--
            left[i] = holdL
            right[i] = holdR
        }
    }

    private companion object {
        const val MAX_BITS = 12f
        const val MIN_BITS = 3f
        const val MAX_HOLD = 9
    }
}
