package id.nextfl.core.dsp

import id.nextfl.core.model.ReverbSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DENORMAL = 1e-18f

private class Comb(size: Int) {
    private val buffer = FloatArray(size)
    private var index = 0
    private var store = 0f
    var feedback = 0.84f
    var damp = 0.2f

    fun process(input: Float): Float {
        val output = buffer[index]
        store = output * (1 - damp) + store * damp
        if (abs(store) < DENORMAL) store = 0f
        buffer[index] = input + store * feedback
        if (++index == buffer.size) index = 0
        return output
    }
}

private class Allpass(size: Int) {
    private val buffer = FloatArray(size)
    private var index = 0

    fun process(input: Float): Float {
        val stored = buffer[index]
        buffer[index] = input + stored * FEEDBACK
        if (++index == buffer.size) index = 0
        return stored - input
    }

    private companion object {
        const val FEEDBACK = 0.5f
    }
}

/** Freeverb with pre-delay and tone. */
class Reverb(private val sampleRate: Int) {
    private val scale = sampleRate / 44_100.0
    private val combsL = COMB_TUNING.map { Comb(scaled(it)) }
    private val combsR = COMB_TUNING.map { Comb(scaled(it + STEREO_SPREAD)) }
    private val allpassL = ALLPASS_TUNING.map { Allpass(scaled(it)) }
    private val allpassR = ALLPASS_TUNING.map { Allpass(scaled(it + STEREO_SPREAD)) }
    private val preDelay = FloatArray((sampleRate * MAX_PRE_DELAY).toInt() + 1)
    private var preDelayIndex = 0
    private var preDelayFrames = 0
    private var toneCoefficient = 0f
    private var toneL = 0f
    private var toneR = 0f
    private var dryGain = 1f
    private var wetGain = 0f
    private var current: ReverbSettings? = null

    fun configure(settings: ReverbSettings) {
        if (settings == current) return
        current = settings
        val feedback = combFeedback(settings.decay.toDouble())
        val damp = (1 - (settings.tone - 1000f) / 17_000f).coerceIn(0f, 1f) * DAMP_SCALE
        (combsL + combsR).forEach { it.feedback = feedback; it.damp = damp }
        preDelayFrames = (settings.preDelay * sampleRate).roundToInt().coerceIn(0, preDelay.size - 1)
        toneCoefficient = exp(-2 * PI * settings.tone / sampleRate).toFloat()
        val mix = if (settings.enabled) settings.mix else 0f
        dryGain = cos(mix * PI / 2).toFloat()
        wetGain = sin(mix * PI / 2).toFloat()
    }

    val isActive: Boolean get() = wetGain > 0f

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        if (!isActive) return
        for (i in 0 until count) {
            val input = delayed((left[i] + right[i]) * INPUT_GAIN)
            var outL = 0f
            var outR = 0f
            for (c in combsL.indices) {
                outL += combsL[c].process(input)
                outR += combsR[c].process(input)
            }
            for (a in allpassL.indices) {
                outL = allpassL[a].process(outL)
                outR = allpassR[a].process(outR)
            }
            toneL = outL + (toneL - outL) * toneCoefficient
            toneR = outR + (toneR - outR) * toneCoefficient
            left[i] = left[i] * dryGain + toneL * wetGain * WET_SCALE
            right[i] = right[i] * dryGain + toneR * wetGain * WET_SCALE
        }
    }

    private fun delayed(input: Float): Float {
        if (preDelayFrames == 0) return input
        val readIndex = (preDelayIndex - preDelayFrames + preDelay.size) % preDelay.size
        val output = preDelay[readIndex]
        preDelay[preDelayIndex] = input
        preDelayIndex = (preDelayIndex + 1) % preDelay.size
        return output
    }

    private fun scaled(samples: Int) = (samples * scale).roundToInt().coerceAtLeast(1)

    companion object {
        private val COMB_TUNING = listOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNING = listOf(556, 441, 341, 225)
        private const val STEREO_SPREAD = 23
        private const val INPUT_GAIN = 0.015f
        private const val WET_SCALE = 3f
        private const val DAMP_SCALE = 0.4f
        private const val MAX_PRE_DELAY = 0.25
        private const val AVERAGE_COMB_SECONDS = 1378.0 / 44_100.0
        private const val MIN_FEEDBACK = 0.7
        private const val MAX_FEEDBACK = 0.98

        // Feedback reaching -60 dB
        fun combFeedback(decaySeconds: Double): Float {
            val passes = decaySeconds / AVERAGE_COMB_SECONDS
            return exp(ln(0.001) / passes).coerceIn(MIN_FEEDBACK, MAX_FEEDBACK).toFloat()
        }
    }
}
