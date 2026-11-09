package id.nextfl.core.analysis

import id.nextfl.core.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Audio features for visuals. */
class AudioFrame {
    val bands = FloatArray(SpectrumAnalyzer.BAND_COUNT)
    var low = 0f
    var mid = 0f
    var high = 0f

    // Band levels mixed by weight
    var drive = 0f
    var energy = 0f
    var kick = 0f
    var beat = false
    var beatStrength = 0f
}

class SpectrumAnalyzer(sampleRate: Int, val fftSize: Int = 2048) {
    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { blackman(it, fftSize) }
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val smoothed = FloatArray(fftSize / 2)
    private val levels = FloatArray(fftSize / 2)
    private val binHz = sampleRate.toFloat() / fftSize
    private val bandBins = logBands()
    private val lowBins = bins(LOW_FROM, LOW_TO)
    private val midBins = bins(MID_FROM, MID_TO)
    private val highBins = bins(HIGH_FROM, HIGH_TO)
    private val lowTracker = BeatTracker()
    private val midTracker = BeatTracker()
    private val highTracker = BeatTracker()
    private var time = 0.0
    private var lastBeat = Double.NEGATIVE_INFINITY
    val frame = AudioFrame()

    /** Expects newest fftSize mono samples. */
    fun analyze(samples: FloatArray, dt: Float, sensitivity: Float, low: Float = 1f, mid: Float = 0f, high: Float = 0f): AudioFrame {
        computeLevels(samples)
        var total = 0f
        bandBins.forEachIndexed { index, range ->
            frame.bands[index] = average(range)
            total += frame.bands[index]
        }
        frame.energy = total / BAND_COUNT
        frame.low = average(lowBins)
        frame.mid = average(midBins)
        frame.high = average(highBins)
        frame.drive = mix(low, mid, high)
        time += dt
        val strength = bandBeats(dt.toDouble(), sensitivity, low, mid, high)
        frame.beat = strength >= MIN_WEIGHTED && time - lastBeat > MERGE_SECONDS
        frame.beatStrength = if (frame.beat) strength else 0f
        if (frame.beat) lastBeat = time
        frame.kick = max(frame.beatStrength, frame.kick * exp(-KICK_DECAY * dt))
        return frame
    }

    // Strongest weighted hit this frame
    private fun bandBeats(dt: Double, sensitivity: Float, low: Float, mid: Float, high: Float): Float {
        val bottom = lowTracker.update(power(lowBins), dt, sensitivity)
        val middle = midTracker.update(power(midBins), dt, sensitivity)
        val top = highTracker.update(power(highBins), dt, sensitivity)
        var strength = 0f
        if (bottom.beat) strength = max(strength, bottom.strength * low)
        if (middle.beat) strength = max(strength, middle.strength * mid)
        if (top.beat) strength = max(strength, top.strength * high)
        return strength
    }

    // Low alone when weights off
    private fun mix(low: Float, mid: Float, high: Float): Float {
        val total = low + mid + high
        if (total < MIN_WEIGHTED) return frame.low
        return (frame.low * low + frame.mid * mid + frame.high * high) / total
    }

    // Mirrors Web Audio analyser scaling
    private fun computeLevels(samples: FloatArray) {
        for (i in 0 until fftSize) {
            real[i] = samples[i] * window[i]
            imag[i] = 0f
        }
        fft.transform(real, imag)
        for (k in smoothed.indices) {
            val magnitude = sqrt(real[k] * real[k] + imag[k] * imag[k]) / fftSize
            smoothed[k] = SMOOTHING * smoothed[k] + (1 - SMOOTHING) * magnitude
            val db = if (smoothed[k] > 0) 20 * log10(smoothed[k]) else MIN_DB
            levels[k] = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
        }
    }

    private fun average(range: IntRange): Float {
        var sum = 0f
        for (k in range) sum += levels[k]
        return sum / max(1, range.count())
    }

    private fun power(range: IntRange): Double {
        var sum = 0.0
        for (k in range) {
            val db = MIN_DB + levels[k] * (MAX_DB - MIN_DB)
            sum += 10.0.pow((db - MAX_DB) / 10.0)
        }
        return sum / max(1, range.count())
    }

    private fun bins(low: Float, high: Float): IntRange {
        val from = min(smoothed.size - 1, floor(low / binHz).toInt())
        val to = min(smoothed.size, max(from + 1, ceil(high / binHz).toInt()))
        return from until to
    }

    private fun logBands(): List<IntRange> {
        val ratio = BAND_MAX_HZ / BAND_MIN_HZ
        return List(BAND_COUNT) { index ->
            bins(BAND_MIN_HZ * ratio.pow(index.toFloat() / BAND_COUNT), BAND_MIN_HZ * ratio.pow((index + 1f) / BAND_COUNT))
        }
    }

    companion object {
        const val BAND_COUNT = 64
        private const val BAND_MIN_HZ = 30f
        private const val BAND_MAX_HZ = 14_000f
        // Contiguous: low includes bass
        private const val LOW_FROM = 30f
        private const val LOW_TO = 250f
        private const val MID_FROM = 250f
        private const val MID_TO = 4000f
        private const val HIGH_FROM = 4000f
        private const val HIGH_TO = 14_000f
        private const val MIN_DB = -90f
        private const val MAX_DB = -20f
        private const val SMOOTHING = 0.2f
        private const val KICK_DECAY = 7f
        private const val MIN_WEIGHTED = 0.02f
        private const val MERGE_SECONDS = 0.06

        private fun blackman(i: Int, n: Int): Float {
            val x = 2 * PI * i / n
            return (0.42 - 0.5 * cos(x) + 0.08 * cos(2 * x)).toFloat()
        }
    }
}
