package id.nextfl.core.dsp

import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.util.AudioMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val DENORMAL = 1e-18f

private class Allpass(size: Int, var gain: Float) {
    private val buffer = FloatArray(size)
    private var index = 0

    fun process(input: Float): Float {
        val delayed = buffer[index]
        val written = input + delayed * gain
        buffer[index] = if (abs(written) < DENORMAL) 0f else written
        if (++index == buffer.size) index = 0
        return delayed - written * gain
    }

    fun clear() = buffer.fill(0f)
}

/** Delay line with fractional reads. */
private class DelayLine(capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0

    fun read(delay: Float): Float {
        var position = writeIndex - delay
        if (position < 0) position += buffer.size
        // Float rounding can reach size
        val index = position.toInt() % buffer.size
        val fraction = (position - position.toInt()).coerceIn(0f, 1f)
        val next = if (index + 1 == buffer.size) 0 else index + 1
        return buffer[index] + (buffer[next] - buffer[index]) * fraction
    }

    fun write(value: Float) {
        buffer[writeIndex] = if (abs(value) < DENORMAL) 0f else value
        if (++writeIndex == buffer.size) writeIndex = 0
    }

    fun clear() = buffer.fill(0f)
}

/** FDN reverb, early reflections, room size. */
class Reverb(private val sampleRate: Int) {
    private val scale = sampleRate / 44_100.0
    private val diffusers = Array(DIFFUSER_LENGTHS.size) { Allpass(scaled(DIFFUSER_LENGTHS[it]), DIFFUSER_GAINS[it]) }
    private val baseLengths = FloatArray(LINES) { scaled(LINE_LENGTHS[it]).toFloat() }
    private val lengths = baseLengths.copyOf()
    private val targetLengths = baseLengths.copyOf()
    private val lines = Array(LINES) { DelayLine((baseLengths[it] * MAX_ROOM_SCALE).toInt() + MOD_DEPTH * 2 + 4) }
    private val early = DelayLine((EARLY_TAP_SECONDS.max() * MAX_ROOM_SCALE * sampleRate).toInt() + 4)
    private val earlyTaps = FloatArray(EARLY_TAP_SECONDS.size)
    private val earlyTargets = FloatArray(EARLY_TAP_SECONDS.size)
    private val lengthGlide = (1 - exp(-1.0 / (ROOM_GLIDE_SECONDS * sampleRate))).toFloat()
    private val outputs = FloatArray(LINES)
    private val damped = FloatArray(LINES)
    private val feedback = FloatArray(LINES)
    private val lfoCos = FloatArray(LINES) { cos(it * 0.9).toFloat() }
    private val lfoSin = FloatArray(LINES) { sin(it * 0.9).toFloat() }
    private val lfoStepCos = FloatArray(LINES) { cos(lfoIncrement(it)).toFloat() }
    private val lfoStepSin = FloatArray(LINES) { sin(lfoIncrement(it)).toFloat() }
    private val maxPreDelayFrames = (sampleRate * MAX_PRE_DELAY).toFloat()
    private val preDelayLine = DelayLine(maxPreDelayFrames.toInt() + PRE_DELAY_GUARD)
    private var lowCutCoefficient = (1 - exp(-2 * PI * DEFAULT_LOW_CUT_HZ / sampleRate)).toFloat()
    private val gainSmoothing = (1 - exp(-1.0 / (GAIN_SMOOTH_SECONDS * sampleRate))).toFloat()
    private var preDelayTarget = 0f
    private var preDelayCurrent = 0f
    private var lowCutState = 0f
    private var damping = 0.3f
    private var toneCoefficient = 0f
    private var toneL = 0f
    private var toneR = 0f
    private var earlyLevel = 0f
    private var width = 1f
    private var dryTarget = 1f
    private var wetTarget = 0f
    private var dry = 1f
    private var wet = 0f
    private var sampleCounter = 0
    private var dirty = false
    private var current: ReverbSettings? = null

    fun configure(settings: ReverbSettings) {
        if (settings == current) return
        val first = current == null
        current = settings
        val room = roomScale(settings.roomSize)
        for (k in 0 until LINES) {
            targetLengths[k] = baseLengths[k] * room
            if (first) lengths[k] = targetLengths[k]
            feedback[k] = lineFeedback(targetLengths[k] / sampleRate, settings.decay.toDouble())
        }
        for (i in earlyTargets.indices) earlyTargets[i] = (EARLY_TAP_SECONDS[i] * room * sampleRate).toFloat()
        damping = settings.damping * MAX_DAMPING
        preDelayTarget = (settings.preDelay * sampleRate).coerceIn(0f, maxPreDelayFrames)
        toneCoefficient = exp(-2 * PI * settings.tone / sampleRate).toFloat()
        lowCutCoefficient = (1 - exp(-2 * PI * settings.lowCut / sampleRate)).toFloat()
        val diffusion = settings.diffusion / DEFAULT_DIFFUSION
        diffusers.forEachIndexed { i, allpass -> allpass.gain = (DIFFUSER_GAINS[i] * diffusion).coerceAtMost(MAX_DIFFUSER_GAIN) }
        earlyLevel = settings.earlyReflections * EARLY_GAIN
        width = settings.width
        // Disabled means clean bypass
        val mix = if (settings.enabled) settings.mix else 0f
        val dryTrim = if (settings.enabled) AudioMath.dbToGain(settings.dryDb) else 1f
        val wetTrim = AudioMath.dbToGain(settings.wetDb)
        dryTarget = cos(mix * PI / 2).toFloat() * dryTrim
        wetTarget = sin(mix * PI / 2).toFloat() * wetTrim
        if (first) {
            dry = dryTarget
            wet = wetTarget
            preDelayCurrent = preDelayTarget
            earlyTargets.copyInto(earlyTaps)
        }
    }

    // Dry trim counts at zero mix
    val isActive: Boolean get() = wetTarget > 0f || wet > SILENT_GAIN || dryTarget != 1f || abs(dry - 1f) > SILENT_GAIN

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        if (!isActive) {
            if (dirty) clear()
            return
        }
        dirty = true
        for (i in 0 until count) {
            val source = lowCut(delayed((left[i] + right[i]) * 0.5f))
            early.write(source)
            glideRoom()
            readLines()
            feedLines(diffuse(source))
            val wetL = wetLeft() + earlyLeft() * earlyLevel
            val wetR = wetRight() + earlyRight() * earlyLevel
            toneL = wetL + (toneL - wetL) * toneCoefficient
            toneR = wetR + (toneR - wetR) * toneCoefficient
            val mid = (toneL + toneR) * 0.5f
            val side = (toneL - toneR) * 0.5f * width
            dry += (dryTarget - dry) * gainSmoothing
            wet += (wetTarget - wet) * gainSmoothing
            left[i] = left[i] * dry + (mid + side) * wet * OUTPUT_GAIN
            right[i] = right[i] * dry + (mid - side) * wet * OUTPUT_GAIN
            advanceLfo()
        }
    }

    // Re-enable starts without stale tail
    private fun clear() {
        dirty = false
        for (line in lines) line.clear()
        for (allpass in diffusers) allpass.clear()
        early.clear()
        preDelayLine.clear()
        damped.fill(0f)
        outputs.fill(0f)
        lowCutState = 0f
        toneL = 0f
        toneR = 0f
    }

    // Odd taps left, even right
    private fun earlyLeft(): Float {
        var sum = 0f
        for (i in earlyTaps.indices step 2) sum += early.read(earlyTaps[i]) * EARLY_TAP_GAINS[i]
        return sum
    }

    private fun earlyRight(): Float {
        var sum = 0f
        for (i in 1 until earlyTaps.size step 2) sum += early.read(earlyTaps[i]) * EARLY_TAP_GAINS[i]
        return sum
    }

    private fun glideRoom() {
        for (k in 0 until LINES) lengths[k] += (targetLengths[k] - lengths[k]) * lengthGlide
        for (i in earlyTaps.indices) earlyTaps[i] += (earlyTargets[i] - earlyTaps[i]) * lengthGlide
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
        preDelayLine.write(input)
        preDelayCurrent += (preDelayTarget - preDelayCurrent) * lengthGlide
        return preDelayLine.read(preDelayCurrent + 1f)
    }

    private fun scaled(samples: Int) = (samples * scale).roundToInt().coerceAtLeast(1)

    private fun roomScale(size: Float) = MIN_ROOM_SCALE + size * (MAX_ROOM_SCALE - MIN_ROOM_SCALE)

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
        private const val PRE_DELAY_GUARD = 4
        private const val DEFAULT_LOW_CUT_HZ = 140.0
        private const val MAX_DAMPING = 0.7f
        private const val DEFAULT_DIFFUSION = 0.7f
        private const val MAX_DIFFUSER_GAIN = 0.85f
        private const val MIN_ROOM_SCALE = 0.5f
        private const val MAX_ROOM_SCALE = 1.5f
        private const val ROOM_GLIDE_SECONDS = 0.15
        private const val EARLY_GAIN = 0.5f

        // Moorer-style reflection pattern
        private val EARLY_TAP_SECONDS = floatArrayOf(0.0043f, 0.0215f, 0.0225f, 0.0268f, 0.0270f, 0.0298f, 0.0458f, 0.0485f, 0.0572f, 0.0587f, 0.0595f, 0.0612f)
        private val EARLY_TAP_GAINS = floatArrayOf(0.841f, 0.504f, 0.491f, 0.379f, 0.380f, 0.346f, 0.289f, 0.272f, 0.192f, 0.193f, 0.217f, 0.181f)
        private const val INPUT_GAIN = 0.35f
        private const val OUTPUT_GAIN = 0.6f
        private const val GAIN_SMOOTH_SECONDS = 0.03
        private const val SILENT_GAIN = 1e-4f

        // Per-line gain for exact RT60
        fun lineFeedback(delaySeconds: Float, decaySeconds: Double): Float =
            10.0.pow(-3.0 * delaySeconds / decaySeconds).toFloat()
    }
}
