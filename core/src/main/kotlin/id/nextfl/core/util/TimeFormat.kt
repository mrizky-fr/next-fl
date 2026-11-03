package id.nextfl.core.util

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object TimeFormat {
    private const val STEPS_PER_BEAT = 4

    fun clock(seconds: Double): String {
        val safe = max(0.0, seconds)
        val minutes = floor(safe / 60).toInt()
        val secs = floor(safe % 60).toInt()
        val centis = floor((safe * 100) % 100).toInt()
        return format("%02d:%02d.%02d", minutes, secs, centis)
    }

    fun barPosition(seconds: Double, bpm: Double): String {
        val steps = floor(Beats.fromSeconds(max(0.0, seconds), bpm) * STEPS_PER_BEAT).toLong()
        val bar = steps / (Beats.PER_BAR * STEPS_PER_BEAT) + 1
        val beat = (steps / STEPS_PER_BEAT) % Beats.PER_BAR + 1
        val step = steps % STEPS_PER_BEAT + 1
        return format("%03d.%d.%d", bar, beat, step)
    }

    fun duration(seconds: Double): String {
        val total = max(0.0, seconds).roundToInt()
        return format("%d:%02d", total / 60, total % 60)
    }

    fun format(pattern: String, vararg args: Any): String = String.format(Locale.ROOT, pattern, *args)
}
