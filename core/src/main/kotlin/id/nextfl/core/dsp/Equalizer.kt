package id.nextfl.core.dsp

import id.nextfl.core.model.EqSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/** EQ parameters that glide smoothly. */
data class EqParams(
    val lowCut: Float,
    val lowGain: Float,
    val midGain: Float,
    val midFreq: Float,
    val highGain: Float,
    val highCut: Float,
) {
    fun approach(target: EqParams, amount: Float): EqParams = EqParams(
        lowCut = glideFrequency(lowCut, target.lowCut, amount),
        lowGain = glideGain(lowGain, target.lowGain, amount),
        midGain = glideGain(midGain, target.midGain, amount),
        midFreq = glideFrequency(midFreq, target.midFreq, amount),
        highGain = glideGain(highGain, target.highGain, amount),
        highCut = glideFrequency(highCut, target.highCut, amount),
    )

    companion object {
        private const val GAIN_SNAP_DB = 0.01f
        private const val FREQUENCY_SNAP_RATIO = 0.001f

        fun of(settings: EqSettings): EqParams =
            if (settings.enabled) {
                EqParams(settings.lowCut, settings.lowGain, settings.midGain, settings.midFreq, settings.highGain, settings.highCut)
            } else {
                EqParams(20f, 0f, 0f, settings.midFreq, 0f, 20_000f)
            }

        private fun glideGain(from: Float, to: Float, amount: Float): Float {
            val next = from + (to - from) * amount
            return if (abs(to - next) < GAIN_SNAP_DB) to else next
        }

        private fun glideFrequency(from: Float, to: Float, amount: Float): Float {
            val next = exp(ln(from) + (ln(to) - ln(from)) * amount)
            return if (abs(next / to - 1f) < FREQUENCY_SNAP_RATIO) to else next
        }
    }
}

/** Five-band equalizer with smoothing. */
class Equalizer(private val sampleRate: Int) {
    private val filters = Array(BAND_COUNT) { StereoBiquad() }
    private val active = BooleanArray(BAND_COUNT)
    private val smoothing = (1 - exp(-SUB_BLOCK / (SMOOTH_SECONDS * sampleRate))).toFloat()
    private var target: EqParams? = null
    private var current: EqParams? = null

    fun configure(settings: EqSettings) {
        val next = EqParams.of(settings)
        target = next
        if (current == null) apply(next)
    }

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val frames = min(SUB_BLOCK, count - offset)
            glide()
            for (band in 0 until BAND_COUNT) if (active[band]) filters[band].process(left, right, offset, frames)
            offset += frames
        }
    }

    private fun glide() {
        val from = current ?: return
        val to = target ?: return
        if (from != to) apply(from.approach(to, smoothing))
    }

    private fun apply(params: EqParams) {
        current = params
        designs(params, sampleRate).forEachIndexed { band, design ->
            val wasActive = active[band]
            active[band] = design != null
            if (design != null) filters[band].set(design)
            if (design != null && !wasActive) filters[band].reset()
        }
    }

    companion object {
        private const val BAND_COUNT = 5
        private const val SUB_BLOCK = 32
        private const val SMOOTH_SECONDS = 0.03
        const val LOW_SHELF_HZ = 120.0
        const val HIGH_SHELF_HZ = 8000.0
        private const val MID_Q = 0.9
        private const val NEUTRAL_GAIN_DB = 0.05f
        private const val LOW_CUT_OFF_HZ = 20.5f
        private const val HIGH_CUT_OFF_HZ = 19_900f

        /** Null means band bypassed. */
        fun designs(params: EqParams, sampleRate: Int): List<BiquadCoefficients?> {
            fun gain(db: Float) = abs(db) >= NEUTRAL_GAIN_DB
            return listOf(
                if (params.lowCut > LOW_CUT_OFF_HZ) BiquadCoefficients.design(FilterType.HIGH_PASS, params.lowCut.toDouble(), sampleRate) else null,
                if (gain(params.lowGain)) BiquadCoefficients.design(FilterType.LOW_SHELF, LOW_SHELF_HZ, sampleRate, gainDb = params.lowGain.toDouble()) else null,
                if (gain(params.midGain)) BiquadCoefficients.design(FilterType.PEAKING, params.midFreq.toDouble(), sampleRate, MID_Q, params.midGain.toDouble()) else null,
                if (gain(params.highGain)) BiquadCoefficients.design(FilterType.HIGH_SHELF, HIGH_SHELF_HZ, sampleRate, gainDb = params.highGain.toDouble()) else null,
                if (params.highCut < HIGH_CUT_OFF_HZ) BiquadCoefficients.design(FilterType.LOW_PASS, params.highCut.toDouble(), sampleRate) else null,
            )
        }

        fun responseDb(settings: EqSettings, frequency: Double, sampleRate: Int): Double =
            designs(EqParams.of(settings), sampleRate).sumOf { it?.magnitudeDb(frequency, sampleRate) ?: 0.0 }
    }
}
