package id.nextfl.core.dsp

import id.nextfl.core.model.StereoizerSettings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow

/** Mono-safe mid/side widener. */
class Stereoizer(private val sampleRate: Int) {
    private val capacity = (MAX_LINE_SECONDS * sampleRate).toInt() + 4
    private val midLine = FloatArray(capacity)
    private val leftLine = FloatArray(capacity)
    private val rightLine = FloatArray(capacity)
    private val glide = (1 - exp(-1.0 / (GLIDE_SECONDS * sampleRate))).toFloat()
    private var index = 0
    private var sideIn = 0f
    private var sideOut = 0f
    private var current: StereoizerSettings? = null

    // Gliding values, then targets
    private var mix = 0f
    private var width = 1f
    private var synth = 0f
    private var comb = 1f
    private var focus = 0f
    private var delayL = 0f
    private var delayR = 0f
    private var gainL = 1f
    private var gainR = 1f
    private var mixTarget = 0f
    private var widthTarget = 1f
    private var synthTarget = 0f
    private var combTarget = 1f
    private var focusTarget = 0f
    private var delayLTarget = 0f
    private var delayRTarget = 0f
    private var gainLTarget = 1f
    private var gainRTarget = 1f

    fun configure(settings: StereoizerSettings) {
        if (settings == current) return
        val first = current == null
        current = settings
        mixTarget = if (settings.enabled) 1f else 0f
        widthTarget = 1f + settings.strength * MAX_WIDEN
        synthTarget = settings.strength * MAX_SYNTH
        combTarget = (MIN_COMB_SECONDS + settings.timbre * (MAX_COMB_SECONDS - MIN_COMB_SECONDS)).toFloat() * sampleRate
        val focusHz = MIN_FOCUS_HZ * (MAX_FOCUS_HZ / MIN_FOCUS_HZ).pow(settings.focus.toDouble())
        focusTarget = exp(-2 * PI * focusHz / sampleRate).toFloat()
        val haas = settings.lrDelay * MAX_HAAS_SECONDS.toFloat() * sampleRate
        delayRTarget = maxOf(haas, 0f)
        delayLTarget = maxOf(-haas, 0f)
        gainLTarget = if (settings.pan > 0) 1 - settings.pan else 1f
        gainRTarget = if (settings.pan < 0) 1 + settings.pan else 1f
        if (first) snap()
    }

    val isActive: Boolean get() = mixTarget > 0f || mix > SILENT

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        if (!isActive) return
        for (i in 0 until count) {
            step()
            val l = left[i]
            val r = right[i]
            val mid = (l + r) * 0.5f
            midLine[index] = mid
            // Complementary comb cancels in mono
            var side = (l - r) * 0.5f * width + read(midLine, comb) * synth
            side = highPass(side)
            leftLine[index] = mid + side
            rightLine[index] = mid - side
            val outL = read(leftLine, delayL) * gainL
            val outR = read(rightLine, delayR) * gainR
            left[i] = l + (outL - l) * mix
            right[i] = r + (outR - r) * mix
            if (++index == capacity) index = 0
        }
    }

    private fun highPass(input: Float): Float {
        sideOut = focus * (sideOut + input - sideIn)
        sideIn = input
        return sideOut
    }

    // Wraps index; float rounding can hit capacity
    private fun read(line: FloatArray, delay: Float): Float {
        if (delay <= 0f) return line[index]
        var position = index - delay
        if (position < 0) position += capacity
        val whole = position.toInt() % capacity
        val fraction = (position - position.toInt()).coerceIn(0f, 1f)
        val next = if (whole + 1 == capacity) 0 else whole + 1
        return line[whole] + (line[next] - line[whole]) * fraction
    }

    private fun step() {
        mix += (mixTarget - mix) * glide
        width += (widthTarget - width) * glide
        synth += (synthTarget - synth) * glide
        comb += (combTarget - comb) * glide
        focus += (focusTarget - focus) * glide
        delayL = settle(delayL + (delayLTarget - delayL) * glide, delayLTarget)
        delayR = settle(delayR + (delayRTarget - delayR) * glide, delayRTarget)
        gainL += (gainLTarget - gainL) * glide
        gainR += (gainRTarget - gainR) * glide
    }

    private fun settle(value: Float, target: Float) = if (kotlin.math.abs(target - value) < DELAY_SNAP_SAMPLES) target else value

    private fun snap() {
        mix = mixTarget
        width = widthTarget
        synth = synthTarget
        comb = combTarget
        focus = focusTarget
        delayL = delayLTarget
        delayR = delayRTarget
        gainL = gainLTarget
        gainR = gainRTarget
    }

    companion object {
        const val MAX_WIDEN = 1f
        const val MAX_SYNTH = 0.6f
        const val MIN_COMB_SECONDS = 0.003
        const val MAX_COMB_SECONDS = 0.02
        const val MIN_FOCUS_HZ = 20.0
        const val MAX_FOCUS_HZ = 300.0
        const val MAX_HAAS_SECONDS = 0.02
        private const val MAX_LINE_SECONDS = 0.025
        private const val GLIDE_SECONDS = 0.02
        private const val SILENT = 1e-4f
        private const val DELAY_SNAP_SAMPLES = 1e-3f
    }
}
