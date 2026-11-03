package id.nextfl.core.dsp

import id.nextfl.core.model.EqSettings
import kotlin.math.abs

/** Five-band channel equalizer. */
class Equalizer(private val sampleRate: Int) {
    private val filters = Array(BAND_COUNT) { StereoBiquad() }
    private val active = BooleanArray(BAND_COUNT)
    private var current: EqSettings? = null

    fun configure(settings: EqSettings) {
        if (settings == current) return
        current = settings
        val designs = designs(settings, sampleRate)
        designs.forEachIndexed { index, design ->
            val wasActive = active[index]
            active[index] = design != null
            if (design != null) filters[index].set(design)
            if (design != null && !wasActive) filters[index].reset()
        }
    }

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        for (index in 0 until BAND_COUNT) if (active[index]) filters[index].process(left, right, count)
    }

    companion object {
        private const val BAND_COUNT = 5
        const val LOW_SHELF_HZ = 120.0
        const val HIGH_SHELF_HZ = 8000.0
        private const val MID_Q = 0.9
        private const val NEUTRAL_GAIN_DB = 0.05f
        private const val LOW_CUT_OFF_HZ = 20.5f
        private const val HIGH_CUT_OFF_HZ = 19_900f

        /** Null means band bypassed. */
        fun designs(settings: EqSettings, sampleRate: Int): List<BiquadCoefficients?> {
            if (!settings.enabled) return List(BAND_COUNT) { null }
            fun gain(db: Float) = abs(db) >= NEUTRAL_GAIN_DB
            return listOf(
                if (settings.lowCut > LOW_CUT_OFF_HZ) BiquadCoefficients.design(FilterType.HIGH_PASS, settings.lowCut.toDouble(), sampleRate) else null,
                if (gain(settings.lowGain)) BiquadCoefficients.design(FilterType.LOW_SHELF, LOW_SHELF_HZ, sampleRate, gainDb = settings.lowGain.toDouble()) else null,
                if (gain(settings.midGain)) BiquadCoefficients.design(FilterType.PEAKING, settings.midFreq.toDouble(), sampleRate, MID_Q, settings.midGain.toDouble()) else null,
                if (gain(settings.highGain)) BiquadCoefficients.design(FilterType.HIGH_SHELF, HIGH_SHELF_HZ, sampleRate, gainDb = settings.highGain.toDouble()) else null,
                if (settings.highCut < HIGH_CUT_OFF_HZ) BiquadCoefficients.design(FilterType.LOW_PASS, settings.highCut.toDouble(), sampleRate) else null,
            )
        }

        fun responseDb(settings: EqSettings, frequency: Double, sampleRate: Int): Double =
            designs(settings, sampleRate).sumOf { it?.magnitudeDb(frequency, sampleRate) ?: 0.0 }
    }
}
