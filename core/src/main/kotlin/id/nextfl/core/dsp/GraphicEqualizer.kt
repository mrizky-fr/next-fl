package id.nextfl.core.dsp

import id.nextfl.core.model.GraphicEqSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** Eight fixed bands, gliding gains. */
class GraphicEqualizer(private val sampleRate: Int) {
    private val filters = Array(BANDS) { StereoBiquad() }
    private val active = BooleanArray(BANDS)
    private val current = FloatArray(BANDS)
    private val target = FloatArray(BANDS)
    private val smoothing = (1 - exp(-SUB_BLOCK / (SMOOTH_SECONDS * sampleRate))).toFloat()

    fun configure(settings: GraphicEqSettings) {
        for (band in 0 until BANDS) target[band] = if (settings.enabled) settings.gains.getOrElse(band) { 0f } else 0f
    }

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val frames = min(SUB_BLOCK, count - offset)
            glide()
            for (band in 0 until BANDS) if (active[band]) filters[band].process(left, right, offset, frames)
            offset += frames
        }
    }

    private fun glide() {
        for (band in 0 until BANDS) {
            if (current[band] == target[band]) continue
            val next = current[band] + (target[band] - current[band]) * smoothing
            current[band] = if (abs(target[band] - next) < SNAP_DB) target[band] else next
            val wasActive = active[band]
            active[band] = abs(current[band]) >= NEUTRAL_DB
            if (active[band]) filters[band].set(design(band, current[band], sampleRate))
            if (active[band] && !wasActive) filters[band].reset()
        }
    }

    companion object {
        private const val BANDS = GraphicEqSettings.BANDS
        private const val SUB_BLOCK = 32
        private const val SMOOTH_SECONDS = 0.03
        private const val SNAP_DB = 0.01f
        private const val NEUTRAL_DB = 0.05f
        private const val BAND_Q = 1.2

        // Shelves at both ends
        fun design(band: Int, gainDb: Float, sampleRate: Int): BiquadCoefficients {
            val frequency = GraphicEqSettings.FREQUENCIES[band].toDouble()
            val type = when (band) {
                0 -> FilterType.LOW_SHELF
                BANDS - 1 -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }
            return BiquadCoefficients.design(type, frequency, sampleRate, BAND_Q, gainDb.toDouble())
        }

        fun responseDb(settings: GraphicEqSettings, frequency: Double, sampleRate: Int): Double {
            if (!settings.enabled) return 0.0
            return (0 until BANDS).sumOf { band ->
                val gain = settings.gains.getOrElse(band) { 0f }
                if (abs(gain) < NEUTRAL_DB) 0.0 else design(band, gain, sampleRate).magnitudeDb(frequency, sampleRate)
            }
        }
    }
}
