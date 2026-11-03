package id.nextfl.core.engine

import id.nextfl.core.dsp.TimeStretcher
import id.nextfl.core.edit.ClipEnvelope
import id.nextfl.core.edit.TrackOps
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.FxChain
import id.nextfl.core.pcm.PcmSource
import id.nextfl.core.util.AudioMath
import kotlin.math.roundToLong

class TrackParams(
    val id: String,
    val fx: FxChain,
    val gain: Float,
    val pan: Float,
)

/** Clip resolved to source frames. */
class ClipVoice(
    val trackId: String,
    val startFrame: Long,
    val endFrame: Long,
    val source: PcmSource,
    val sourceStart: Double,
    val step: Double,
    val envelope: ClipEnvelope,
)

/** Immutable snapshot for rendering. */
class RenderGraph(
    val tracks: List<TrackParams>,
    val voices: List<ClipVoice>,
    val masterGain: Float,
    val endFrame: Long,
    val tailFrames: Long,
    val masterFx: FxChain = FxChain(),
) {
    companion object {
        val EMPTY = RenderGraph(emptyList(), emptyList(), 1f, 0, 0)
    }
}

interface SourceResolver {
    fun original(assetId: String): PcmSource?
    fun stretched(assetId: String, rate: Double): PcmSource?
}

object RenderGraphBuilder {
    private const val MIN_TAIL_SECONDS = 0.5

    fun build(doc: ProjectDoc, assets: AssetMap, sampleRate: Int, resolver: SourceResolver): RenderGraph {
        val tracks = doc.tracks.map { track ->
            val audible = TrackOps.isAudible(track, doc.tracks)
            TrackParams(track.id, track.fx, if (audible) AudioMath.dbToGain(track.volumeDb) else 0f, track.pan)
        }
        val trackIds = tracks.map { it.id }.toSet()
        val voices = doc.clips.filter { it.trackId in trackIds }.mapNotNull { clip ->
            val original = resolver.original(clip.assetId) ?: return@mapNotNull null
            val span = clipSpan(clip, assets, doc.bpm)
            val stretched = if (clip.keepPitch && !TimeStretcher.isUnity(span.rate)) resolver.stretched(clip.assetId, span.rate) else null
            val source = stretched ?: original
            val sourceRate = source.sampleRate.toDouble()
            val sourceOffset = if (stretched != null) clip.offset / span.rate else clip.offset
            val step = (if (stretched != null) 1.0 else span.rate) * sourceRate / sampleRate
            ClipVoice(
                trackId = clip.trackId,
                startFrame = (span.start * sampleRate).roundToLong(),
                endFrame = (span.end * sampleRate).roundToLong(),
                source = source,
                sourceStart = sourceOffset * sourceRate,
                step = step,
                envelope = ClipEnvelope(span.duration, AudioMath.dbToGain(clip.gainDb).toDouble(), clip.fadeIn, clip.fadeOut),
            )
        }
        val end = voices.maxOfOrNull { it.endFrame } ?: 0L
        val reverbs = doc.tracks.map { it.reverb } + doc.masterFx.reverb
        val tail = reverbs.filter { it.enabled }.maxOfOrNull { (it.decay + it.preDelay).toDouble() } ?: 0.0
        return RenderGraph(
            tracks = tracks,
            voices = voices,
            masterGain = AudioMath.dbToGain(doc.masterDb),
            endFrame = end,
            tailFrames = ((tail + MIN_TAIL_SECONDS) * sampleRate).roundToLong(),
            masterFx = doc.masterFx,
        )
    }
}
