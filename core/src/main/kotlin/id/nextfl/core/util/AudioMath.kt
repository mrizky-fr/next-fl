package id.nextfl.core.util

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object AudioMath {
    const val MIN_DB = -60f
    private const val FADER_HEADROOM = 2f

    fun dbToGain(db: Float): Float = if (db <= MIN_DB) 0f else 10f.pow(db / 20f)

    fun gainToDb(gain: Float): Float = if (gain <= 0f) MIN_DB else max(MIN_DB, 20f * log10(gain))

    // Unity gain near 0.707
    fun faderToDb(position: Float): Float = gainToDb(position * position * FADER_HEADROOM)

    fun dbToFader(db: Float): Float = sqrt(dbToGain(db) / FADER_HEADROOM)

    fun semitonesToRatio(semitones: Double): Double = 2.0.pow(semitones / 12)
}

object Beats {
    const val PER_BAR = 4

    fun toSeconds(beats: Double, bpm: Double): Double = beats * 60.0 / bpm

    fun fromSeconds(seconds: Double, bpm: Double): Double = seconds * bpm / 60.0
}
