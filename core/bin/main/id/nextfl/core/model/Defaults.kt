package id.nextfl.core.model

import id.nextfl.core.util.Ids

object Defaults {
    val TRACK_COLORS = listOf(
        0xFF6366F1,
        0xFF8B5CF6,
        0xFFB45CFF,
        0xFF22B8E6,
        0xFFFF5C8A,
        0xFF14B8A6,
        0xFFF5A524,
        0xFF6B9BFF,
    )

    private const val INITIAL_TRACKS = 3

    fun track(index: Int): Track = Track(
        id = Ids.create("trk"),
        name = "Track ${index + 1}",
        color = TRACK_COLORS[index % TRACK_COLORS.size],
    )

    fun project(): ProjectDoc = ProjectDoc(tracks = List(INITIAL_TRACKS) { track(it) })

    fun clip(trackId: String, assetId: String, name: String, startBeat: Double, length: Double): Clip = Clip(
        id = Ids.create("clp"),
        trackId = trackId,
        assetId = assetId,
        name = name,
        startBeat = startBeat,
        offset = 0.0,
        length = length,
    )
}
