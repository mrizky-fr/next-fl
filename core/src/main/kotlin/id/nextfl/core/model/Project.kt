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

/** Tone is the wet high cut. */
@Serializable
data class ReverbSettings(
    val enabled: Boolean = false,
    val mix: Float = 0.25f,
    val decay: Float = 2.2f,
    val preDelay: Float = 0.02f,
    val tone: Float = 9000f,
    val damping: Float = 0.35f,
    val earlyReflections: Float = 0.3f,
    val roomSize: Float = 0.5f,
    val lowCut: Float = 140f,
    val diffusion: Float = 0.7f,
    val width: Float = 1f,
    val dryDb: Float = 0f,
    val wetDb: Float = 0f,
)

/** Values 0..1, delay and pan bipolar. */
@Serializable
data class StereoizerSettings(
    val enabled: Boolean = false,
    val strength: Float = 0.5f,
    val timbre: Float = 0.5f,
    val focus: Float = 1f,
    val lrDelay: Float = 0f,
    val pan: Float = 0f,
)

@Serializable
data class GraphicEqSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(BANDS) { 0f },
) {
    companion object {
        const val BANDS = 8
        const val MAX_GAIN_DB = 12f
        val FREQUENCIES = floatArrayOf(80f, 160f, 375f, 750f, 1500f, 3000f, 6000f, 12_000f)
    }
}

@Serializable
data class LevellerSettings(
    val enabled: Boolean = false,
    val gainDb: Float = 0f,
    val pan: Float = 0f,
) {
    companion object {
        const val MAX_GAIN_DB = 12f
    }
}

/** Insert chain, top to bottom. */
@Serializable
data class FxChain(
    val eq: EqSettings = EqSettings(),
    val stereoizer: StereoizerSettings = StereoizerSettings(),
    val reverb: ReverbSettings = ReverbSettings(),
    val graphicEq: GraphicEqSettings = GraphicEqSettings(),
    val leveller: LevellerSettings = LevellerSettings(),
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
    val stereoizer: StereoizerSettings = StereoizerSettings(),
    val graphicEq: GraphicEqSettings = GraphicEqSettings(),
    val leveller: LevellerSettings = LevellerSettings(),
) {
    // Flat fields keep old projects loading
    val fx: FxChain get() = FxChain(eq, stereoizer, reverb, graphicEq, leveller)

    fun withFx(chain: FxChain): Track =
        copy(eq = chain.eq, stereoizer = chain.stereoizer, reverb = chain.reverb, graphicEq = chain.graphicEq, leveller = chain.leveller)
}

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
    val masterFx: FxChain = FxChain(),
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
