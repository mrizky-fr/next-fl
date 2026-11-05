package id.nextfl.core.engine

import id.nextfl.core.edit.ClipEnvelope
import id.nextfl.core.edit.TrackOps
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.edit.clipVoicing
import id.nextfl.core.dsp.DjEffects
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.DjSettings
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
    val id: String,
    val trackId: String,
    val startFrame: Long,
    val endFrame: Long,
    val source: PcmSource,
    val sourceStart: Double,
    val step: Double,
    val envelope: ClipEnvelope,
    // Per-frame read rate inside grains
    val grainStep: Double = step,
    val granular: Boolean = false,
)

/** Immutable snapshot for rendering. */
class RenderGraph(
    val tracks: List<TrackParams>,
    val voices: List<ClipVoice>,
    val masterGain: Float,
    val endFrame: Long,
    val tailFrames: Long,
    val masterFx: FxChain = FxChain(),
    val dj: DjSettings = DjSettings(),
    val bpm: Double = ProjectDoc.DEFAULT_BPM,
) {
    companion object {
        val EMPTY = RenderGraph(emptyList(), emptyList(), 1f, 0, 0)
    }
}

fun interface SourceResolver {
    fun original(assetId: String): PcmSource?
}

object RenderGraphBuilder {
    private const val MIN_TAIL_SECONDS = 0.5
    private const val ECHO_TAIL_SECONDS = 3.0

    fun build(doc: ProjectDoc, assets: AssetMap, sampleRate: Int, resolver: SourceResolver): RenderGraph {
        val tracks = doc.tracks.map { track ->
            val audible = TrackOps.isAudible(track, doc.tracks)
            TrackParams(track.id, track.fx, if (audible) AudioMath.dbToGain(track.volumeDb) else 0f, track.pan)
        }
        val trackIds = tracks.map { it.id }.toSet()
        val voices = doc.clips.filter { it.trackId in trackIds }.mapNotNull { clip ->
            val original = resolver.original(clip.assetId) ?: return@mapNotNull null
            val span = clipSpan(clip, assets, doc.bpm)
            val voicing = clipVoicing(clip, span.rate)
            val sourceRate = original.sampleRate.toDouble()
            val ratio = sourceRate / sampleRate
            ClipVoice(
                id = clip.id,
                trackId = clip.trackId,
                startFrame = (span.start * sampleRate).roundToLong(),
                endFrame = (span.end * sampleRate).roundToLong(),
                source = original,
                sourceStart = clip.offset * sourceRate,
                step = span.rate * ratio,
                envelope = ClipEnvelope(span.duration, AudioMath.dbToGain(clip.gainDb).toDouble(), clip.fadeIn, clip.fadeOut),
                grainStep = voicing.pitch * ratio,
                granular = voicing.needsStretch,
            )
        }
        val end = voices.maxOfOrNull { it.endFrame } ?: 0L
        val reverbs = doc.tracks.map { it.reverb } + doc.masterFx.reverb
        val reverbTail = reverbs.filter { it.enabled }.maxOfOrNull { (it.decay + it.preDelay).toDouble() } ?: 0.0
        val tail = if (doc.dj.echo >= DjEffects.OFF) maxOf(reverbTail, ECHO_TAIL_SECONDS) else reverbTail
        return RenderGraph(
            tracks = tracks,
            voices = voices,
            masterGain = AudioMath.dbToGain(doc.masterDb),
            endFrame = end,
            tailFrames = ((tail + MIN_TAIL_SECONDS) * sampleRate).roundToLong(),
            masterFx = doc.masterFx,
            dj = doc.dj,
            bpm = doc.bpm,
        )
    }
}
