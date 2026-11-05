package id.nextfl.core.edit

import id.nextfl.core.dsp.TimeStretcher
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.Clip
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.util.AudioMath
import id.nextfl.core.util.Beats
import kotlin.math.max
import kotlin.math.min

const val MIN_CLIP_SECONDS = 0.05
const val DECLICK_SECONDS = 0.004

data class ClipSpan(val start: Double, val end: Double, val duration: Double, val rate: Double)

/** WSOLA rate, then resample ratio. */
data class ClipVoicing(val stretch: Double, val pitch: Double) {
    val needsStretch: Boolean get() = !TimeStretcher.isUnity(stretch)
}

// Stretch then resample shifts pitch
fun clipVoicing(clip: Clip, rate: Double): ClipVoicing {
    val pitch = (if (clip.keepPitch) 1.0 else rate) * AudioMath.semitonesToRatio(clip.pitchSemitones)
    return ClipVoicing(rate / pitch, pitch)
}

fun clipRate(clip: Clip, assetBpm: Double?, bpm: Double): Double =
    if (clip.syncTempo && assetBpm != null && assetBpm > 0) bpm / assetBpm else clip.speed

fun clipSpan(clip: Clip, assets: AssetMap, bpm: Double): ClipSpan {
    val rate = clipRate(clip, assets[clip.assetId]?.bpm, bpm)
    val start = Beats.toSeconds(clip.startBeat, bpm)
    val duration = clip.length / rate
    return ClipSpan(start, start + duration, duration, rate)
}

fun projectEndSeconds(doc: ProjectDoc, assets: AssetMap): Double =
    doc.clips.maxOfOrNull { clipSpan(it, assets, doc.bpm).end } ?: 0.0

/** Trapezoid envelope with declick. */
class ClipEnvelope(private val duration: Double, private val gain: Double, fadeIn: Double, fadeOut: Double) {
    private val attack: Double
    private val release: Double

    init {
        var a = max(fadeIn, DECLICK_SECONDS)
        var r = max(fadeOut, DECLICK_SECONDS)
        if (a + r > duration) {
            val scale = duration / (a + r)
            a *= scale
            r *= scale
        }
        attack = a
        release = r
    }

    fun valueAt(time: Double): Double {
        if (time <= 0 || time >= duration) return 0.0
        val rampIn = if (attack > 0) time / attack else 1.0
        val rampOut = if (release > 0) (duration - time) / release else 1.0
        return gain * min(1.0, min(rampIn, rampOut))
    }
}
