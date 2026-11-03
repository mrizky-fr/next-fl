package id.nextfl.core.dsp

import id.nextfl.core.model.ReverbSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val DENORMAL = 1e-18f

private class Allpass(size: Int, private val gain: Float) {
    private val buffer = FloatArray(size)
    private var index = 0

    fun process(input: Float): Float {
        val delayed = buffer[index]
        val written = input + delayed * gain
        buffer[index] = if (abs(written) < DENORMAL) 0f else written
        if (++index == buffer.size) index = 0
        return delayed - written * gain
    }
}

/** Delay line with fractional reads. */
private class DelayLine(capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0

    fun read(delay: Float): Float {
        var position = writeIndex - delay
        if (position < 0) position += buffer.size
        val index = position.toInt()
        val fraction = position - index
        val next = if (index + 1 == buffer.size) 0 else index + 1
        return buffer[index] + (buffer[next] - buffer[index]) * fraction
    }

    fun write(value: Float) {
        buffer[writeIndex] = if (abs(value) < DENORMAL) 0f else value
        if (++writeIndex == buffer.size) writeIndex = 0
    }
}

/** Smooth FDN reverb with diffusion. */
class Reverb(private val sampleRate: Int) {
    private val scale = sampleRate / 44_100.0
    private val diffusers = DIFFUSER_LENGTHS.indices.map { Allpass(scaled(DIFFUSER_LENGTHS[it]), DIFFUSER_GAINS[it]) }
    private val lengths = FloatArray(LINES) { scaled(LINE_LENGTHS[it]).toFloat() }
    private val lines = Array(LINES) { DelayLine(lengths[it].toInt() + MOD_DEPTH * 2 + 4) }
    private val outputs = FloatArray(LINES)
    private val damped = FloatArray(LINES)
    private val feedback = FloatArray(LINES)
    private val lfoCos = FloatArray(LINES) { cos(it * 0.9).toFloat() }
    private val lfoSin = FloatArray(LINES) { sin(it * 0.9).toFloat() }
    private val lfoStepCos = FloatArray(LINES) { cos(lfoIncrement(it)).toFloat() }
    private val lfoStepSin = FloatArray(LINES) { sin(lfoIncrement(it)).toFloat() }
    private val preDelay = FloatArray((sampleRate * MAX_PRE_DELAY).toInt() + 1)
    private val lowCutCoefficient = (1 - exp(-2 * PI * LOW_CUT_HZ / sampleRate)).toFloat()
    private val gainSmoothing = (1 - exp(-1.0 / (GAIN_SMOOTH_SECONDS * sampleRate))).toFloat()
    private var preDelayIndex = 0
    private var preDelayFrames = 0
    private var lowCutState = 0f
    private var damping = 0.3f
    private var toneCoefficient = 0f
    private var toneL = 0f
    private var toneR = 0f
    private var dryTarget = 1f
    private var wetTarget = 0f
    private var dry = 1f
    private var wet = 0f
    private var sampleCounter = 0
    private var current: ReverbSettings? = null

    fun configure(settings: ReverbSettings) {
        if (settings == current) return
        current = settings
        for (k in 0 until LINES) feedback[k] = lineFeedback(lengths[k] / sampleRate, settings.decay.toDouble())
        damping = (1 - (settings.tone - 1000f) / 17_000f).coerceIn(0f, 1f) * MAX_DAMPING
        preDelayFrames = (settings.preDelay * sampleRate).roundToInt().coerceIn(0, preDelay.size - 1)
        toneCoefficient = exp(-2 * PI * settings.tone / sampleRate).toFloat()
        val mix = if (settings.enabled) settings.mix else 0f
        dryTarget = cos(mix * PI / 2).toFloat()
        wetTarget = sin(mix * PI / 2).toFloat()
    }

    val isActive: Boolean get() = wetTarget > 0f || wet > SILENT_GAIN

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        if (!isActive) return
        for (i in 0 until count) {
            val input = diffuse(lowCut(delayed((left[i] + right[i]) * 0.5f)))
            readLines()
            feedLines(input)
            val wetL = wetLeft()
            val wetR = wetRight()
            toneL = wetL + (toneL - wetL) * toneCoefficient
            toneR = wetR + (toneR - wetR) * toneCoefficient
            dry += (dryTarget - dry) * gainSmoothing
            wet += (wetTarget - wet) * gainSmoothing
            left[i] = left[i] * dry + toneL * wet * OUTPUT_GAIN
            right[i] = right[i] * dry + toneR * wet * OUTPUT_GAIN
            advanceLfo()
        }
    }

    private fun diffuse(input: Float): Float {
        var value = input
        for (allpass in diffusers) value = allpass.process(value)
        return value
    }

    private fun readLines() {
        for (k in 0 until LINES) outputs[k] = lines[k].read(lengths[k] + lfoSin[k] * MOD_DEPTH)
    }

    // Householder mix keeps energy
    private fun feedLines(input: Float) {
        var sum = 0f
        for (k in 0 until LINES) sum += outputs[k]
        val reflection = sum * 2f / LINES
        for (k in 0 until LINES) {
            val mixed = outputs[k] - reflection
            damped[k] = mixed + (damped[k] - mixed) * damping
            lines[k].write(damped[k] * feedback[k] + input * INPUT_SIGNS[k] * INPUT_GAIN)
        }
    }

    private fun wetLeft(): Float = outputs[0] + outputs[1] - outputs[2] + outputs[3] - outputs[4] + outputs[5] + outputs[6] - outputs[7]

    private fun wetRight(): Float = outputs[0] - outputs[1] + outputs[2] + outputs[3] - outputs[4] - outputs[5] + outputs[6] + outputs[7]

    private fun advanceLfo() {
        for (k in 0 until LINES) {
            val c = lfoCos[k] * lfoStepCos[k] - lfoSin[k] * lfoStepSin[k]
            val s = lfoSin[k] * lfoStepCos[k] + lfoCos[k] * lfoStepSin[k]
            lfoCos[k] = c
            lfoSin[k] = s
        }
        if (++sampleCounter % RENORMALIZE_EVERY == 0) {
            for (k in 0 until LINES) {
                val norm = sqrt(lfoCos[k] * lfoCos[k] + lfoSin[k] * lfoSin[k])
                lfoCos[k] /= norm
                lfoSin[k] /= norm
            }
        }
    }

    private fun lowCut(input: Float): Float {
        lowCutState += lowCutCoefficient * (input - lowCutState)
        return input - lowCutState
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

    private fun lfoIncrement(line: Int) = 2 * PI * (LFO_BASE_HZ + line * LFO_SPREAD_HZ) / sampleRate

    companion object {
        private const val LINES = 8
        private val LINE_LENGTHS = intArrayOf(1031, 1327, 1523, 1811, 2069, 2297, 2539, 2953)
        private val INPUT_SIGNS = floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f)
        private val DIFFUSER_LENGTHS = intArrayOf(210, 158, 561, 410)
        private val DIFFUSER_GAINS = floatArrayOf(0.75f, 0.75f, 0.625f, 0.625f)
        private const val MOD_DEPTH = 6
        private const val LFO_BASE_HZ = 0.31
        private const val LFO_SPREAD_HZ = 0.083
        private const val RENORMALIZE_EVERY = 4096
        private const val MAX_PRE_DELAY = 0.25
        private const val LOW_CUT_HZ = 140.0
        private const val MAX_DAMPING = 0.55f
        private const val INPUT_GAIN = 0.35f
        private const val OUTPUT_GAIN = 0.6f
        private const val GAIN_SMOOTH_SECONDS = 0.03
        private const val SILENT_GAIN = 1e-4f

        // Per-line gain for exact RT60
        fun lineFeedback(delaySeconds: Float, decaySeconds: Double): Float =
            10.0.pow(-3.0 * delaySeconds / decaySeconds).toFloat()
    }
}
