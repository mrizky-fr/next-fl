package id.nextfl.core.model

import id.nextfl.core.util.Ids

object Defaults {
    // Tints and hue shifts of #0465C4
    val TRACK_COLORS = listOf(
        0xFF0465C4,
        0xFF3C8DDB,
        0xFF0A8DB8,
        0xFF3F5FD1,
        0xFF6AAEEA,
        0xFF034C93,
        0xFF1AA6CC,
        0xFF7391E3,
    )

    // Earlier default palettes, migrated on load
    private val LEGACY_TRACK_COLORS = listOf(
        listOf(0xFF5B7CFF, 0xFF8466FF, 0xFFB45CFF, 0xFF22B8E6, 0xFFFF5C8A, 0xFF14B8A6, 0xFFF5A524, 0xFF6B9BFF),
        listOf(0xFF6366F1, 0xFF8B5CF6, 0xFFB45CFF, 0xFF22B8E6, 0xFFFF5C8A, 0xFF14B8A6, 0xFFF5A524, 0xFF6B9BFF),
    )

    private const val INITIAL_TRACKS = 3

    fun migrateTrackColor(color: Long): Long {
        val index = LEGACY_TRACK_COLORS.firstNotNullOfOrNull { palette -> palette.indexOf(color).takeIf { it >= 0 } }
        return index?.let { TRACK_COLORS[it % TRACK_COLORS.size] } ?: color
    }

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
