package id.nextfl.core.edit

import id.nextfl.core.model.Defaults
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.Track

object TrackOps {
    fun add(doc: ProjectDoc): ProjectDoc = doc.copy(tracks = doc.tracks + Defaults.track(doc.tracks.size))

    fun remove(doc: ProjectDoc, trackId: String): ProjectDoc = doc.copy(
        tracks = doc.tracks.filterNot { it.id == trackId },
        clips = doc.clips.filterNot { it.trackId == trackId },
    )

    fun update(doc: ProjectDoc, trackId: String, transform: (Track) -> Track): ProjectDoc =
        doc.copy(tracks = doc.tracks.map { if (it.id == trackId) transform(it) else it })

    // Solo overrides mute
    fun isAudible(track: Track, tracks: List<Track>): Boolean =
        if (tracks.any { it.solo }) track.solo else !track.mute
}
