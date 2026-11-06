package id.nextfl.core.dsp

import id.nextfl.core.model.CompressorSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/** Stereo-linked soft-knee compressor. */
class Compressor(private val sampleRate: Int) {
    private val glide = (1 - exp(-1.0 / (GLIDE_SECONDS * sampleRate))).toFloat()
    private var current: CompressorSettings? = null
    private var thresholdDb = 0f
    private var ratio = 1f
    private var kneeDb = 0f
    private var kneeStart = 1f
    private var attack = 0f
    private var release = 0f
    private var makeup = 1f
    private var makeupTarget = 1f
    private var mix = 0f
    private var mixTarget = 0f
    private var envelopeDb = 0f

    // Block peak, read by meters
    @Volatile
    var gainReductionDb = 0f
        private set

    fun configure(settings: CompressorSettings) {
        if (settings == current) return
        val first = current == null
        current = settings
        thresholdDb = settings.thresholdDb
        ratio = settings.ratio.coerceAtLeast(1f)
        kneeDb = settings.kneeDb.coerceAtLeast(0f)
        kneeStart = dbToLinear(thresholdDb - kneeDb / 2)
        attack = coefficient(settings.attackMs)
        release = coefficient(settings.releaseMs)
        makeupTarget = dbToLinear(totalMakeupDb(settings))
        mixTarget = if (settings.enabled) 1f else 0f
        if (first) {
            makeup = makeupTarget
            mix = mixTarget
        }
    }

    val isActive: Boolean get() = mixTarget > 0f || mix > SILENT

    fun process(left: FloatArray, right: FloatArray, count: Int) {
        if (!isActive) {
            envelopeDb = 0f
            gainReductionDb = 0f
            return
        }
        var blockReduction = 0f
        for (i in 0 until count) {
            val l = left[i]
            val r = right[i]
            val target = detect(max(abs(l), abs(r)))
            val speed = if (target > envelopeDb) attack else release
            envelopeDb = target + (envelopeDb - target) * speed
            if (envelopeDb < FLUSH_DB) envelopeDb = 0f
            makeup += (makeupTarget - makeup) * glide
            mix += (mixTarget - mix) * glide
            val compression = if (envelopeDb == 0f) 1f else dbToLinear(-envelopeDb)
            val gain = 1f + (compression * makeup - 1f) * mix
            left[i] = l * gain
            right[i] = r * gain
            blockReduction = max(blockReduction, envelopeDb)
        }
        gainReductionDb = blockReduction * mix
    }

    // Skips log below knee
    private fun detect(peak: Float): Float =
        if (peak <= kneeStart) 0f else reductionDb(linearToDb(peak), thresholdDb, ratio, kneeDb)

    private fun coefficient(milliseconds: Float): Float =
        exp(-1.0 / (milliseconds.coerceAtLeast(MIN_TIME_MS).toDouble() / MS_PER_SECOND * sampleRate)).toFloat()

    companion object {
        private const val GLIDE_SECONDS = 0.02
        private const val SILENT = 1e-4f
        private const val FLUSH_DB = 1e-4f
        private const val MIN_TIME_MS = 0.05f
        private const val MS_PER_SECOND = 1000.0
        private const val DB_PER_NEPER = 8.685889638f
        private const val AUTO_MAKEUP_SHARE = 0.5f

        /** Static curve, positive dB. */
        fun reductionDb(inputDb: Float, thresholdDb: Float, ratio: Float, kneeDb: Float): Float {
            val slope = 1f - 1f / ratio.coerceAtLeast(1f)
            val over = inputDb - thresholdDb
            val halfKnee = kneeDb.coerceAtLeast(0f) / 2
            return when {
                over <= -halfKnee -> 0f
                over < halfKnee -> slope * (over + halfKnee) * (over + halfKnee) / (4 * halfKnee)
                else -> slope * over
            }
        }

        // Half the full-scale reduction
        fun autoMakeupDb(settings: CompressorSettings): Float =
            reductionDb(0f, settings.thresholdDb, settings.ratio, settings.kneeDb) * AUTO_MAKEUP_SHARE

        fun totalMakeupDb(settings: CompressorSettings): Float =
            settings.makeupDb + if (settings.autoMakeup) autoMakeupDb(settings) else 0f

        fun outputDb(settings: CompressorSettings, inputDb: Float): Float =
            inputDb - reductionDb(inputDb, settings.thresholdDb, settings.ratio, settings.kneeDb) + totalMakeupDb(settings)

        private fun dbToLinear(db: Float): Float = exp(db / DB_PER_NEPER)

        private fun linearToDb(gain: Float): Float = ln(gain) * DB_PER_NEPER
    }
}
