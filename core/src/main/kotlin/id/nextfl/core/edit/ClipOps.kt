package id.nextfl.core.edit

import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.Clip
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.util.Beats

object ClipOps {
    fun add(doc: ProjectDoc, clip: Clip): ProjectDoc = doc.copy(clips = doc.clips + clip)

    fun update(doc: ProjectDoc, clipId: String, transform: (Clip) -> Clip): ProjectDoc =
        doc.copy(clips = doc.clips.map { if (it.id == clipId) transform(it) else it })

    fun remove(doc: ProjectDoc, clipId: String): ProjectDoc = doc.copy(clips = doc.clips.filterNot { it.id == clipId })

    fun split(doc: ProjectDoc, assets: AssetMap, clipId: String, atSeconds: Double, newId: String): ProjectDoc {
        val clip = doc.clips.find { it.id == clipId } ?: return doc
        val span = clipSpan(clip, assets, doc.bpm)
        val leftDuration = atSeconds - span.start
        val rightDuration = span.end - atSeconds
        if (leftDuration < MIN_CLIP_SECONDS || rightDuration < MIN_CLIP_SECONDS) return doc

        val leftLength = leftDuration * span.rate
        val left = clip.copy(length = leftLength, fadeIn = minOf(clip.fadeIn, leftDuration), fadeOut = 0.0)
        val right = clip.copy(
            id = newId,
            startBeat = Beats.fromSeconds(atSeconds, doc.bpm),
            offset = clip.offset + leftLength,
            length = clip.length - leftLength,
            fadeIn = 0.0,
            fadeOut = minOf(clip.fadeOut, rightDuration),
        )
        return doc.copy(clips = doc.clips.flatMap { if (it.id == clipId) listOf(left, right) else listOf(it) })
    }

    fun duplicate(doc: ProjectDoc, assets: AssetMap, clipId: String, newId: String): ProjectDoc {
        val clip = doc.clips.find { it.id == clipId } ?: return doc
        val span = clipSpan(clip, assets, doc.bpm)
        return add(doc, clip.copy(id = newId, startBeat = Beats.fromSeconds(span.end, doc.bpm)))
    }

    data class TrimContext(val rate: Double, val bpm: Double, val assetDuration: Double)

    fun trimStart(clip: Clip, newStartSeconds: Double, context: TrimContext): Clip {
        val start = Beats.toSeconds(clip.startBeat, context.bpm)
        val duration = clip.length / context.rate
        val minDelta = maxOf(-clip.offset / context.rate, -start)
        val delta = (newStartSeconds - start).coerceIn(minDelta, duration - MIN_CLIP_SECONDS)
        return clip.copy(
            startBeat = Beats.fromSeconds(start + delta, context.bpm),
            offset = clip.offset + delta * context.rate,
            length = clip.length - delta * context.rate,
        )
    }

    fun trimEnd(clip: Clip, newEndSeconds: Double, context: TrimContext): Clip {
        val start = Beats.toSeconds(clip.startBeat, context.bpm)
        val maxDuration = (context.assetDuration - clip.offset) / context.rate
        val duration = (newEndSeconds - start).coerceIn(MIN_CLIP_SECONDS, maxOf(MIN_CLIP_SECONDS, maxDuration))
        return clip.copy(length = duration * context.rate)
    }

    fun isTrackFree(doc: ProjectDoc, assets: AssetMap, trackId: String, start: Double, end: Double): Boolean =
        doc.clips.filter { it.trackId == trackId }.all {
            val span = clipSpan(it, assets, doc.bpm)
            span.end <= start || span.start >= end
        }

    fun findFreeTrackId(doc: ProjectDoc, assets: AssetMap, start: Double, end: Double): String? =
        doc.tracks.firstOrNull { isTrackFree(doc, assets, it.id, start, end) }?.id
}
