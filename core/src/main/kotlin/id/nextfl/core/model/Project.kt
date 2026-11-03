package id.nextfl.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EqSettings(
    val enabled: Boolean = true,
    val lowCut: Float = 20f,
    val lowGain: Float = 0f,
    val midGain: Float = 0f,
    val midFreq: Float = 1000f,
    val highGain: Float = 0f,
    val highCut: Float = 20_000f,
)

@Serializable
data class ReverbSettings(
    val enabled: Boolean = false,
    val mix: Float = 0.25f,
    val decay: Float = 2.2f,
    val preDelay: Float = 0.02f,
    val tone: Float = 9000f,
)

@Serializable
data class Track(
    val id: String,
    val name: String,
    val color: Long,
    val volumeDb: Float = 0f,
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val eq: EqSettings = EqSettings(),
    val reverb: ReverbSettings = ReverbSettings(),
)

/** Start in beats, source seconds. */
@Serializable
data class Clip(
    val id: String,
    val trackId: String,
    val assetId: String,
    val name: String,
    val startBeat: Double,
    val offset: Double,
    val length: Double,
    val gainDb: Float = 0f,
    val fadeIn: Double = 0.0,
    val fadeOut: Double = 0.0,
    val syncTempo: Boolean = false,
    val speed: Double = 1.0,
    val keepPitch: Boolean = true,
)

@Serializable
data class ProjectDoc(
    val bpm: Double = DEFAULT_BPM,
    val masterDb: Float = 0f,
    val tracks: List<Track> = emptyList(),
    val clips: List<Clip> = emptyList(),
) {
    companion object {
        const val DEFAULT_BPM = 128.0
        const val MIN_BPM = 40.0
        const val MAX_BPM = 300.0
    }
}

@Serializable
data class AudioAsset(
    val id: String,
    val name: String,
    val duration: Double,
    val bpm: Double? = null,
)

typealias AssetMap = Map<String, AudioAsset>
