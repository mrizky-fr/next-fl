package id.nextfl.core.model

class FxPreset<T>(val name: String, val settings: T)

/** Starting points per module. */
object FxPresets {
    const val CUSTOM = "Custom"

    val eq = listOf(
        FxPreset("Default", EqSettings(enabled = true)),
        FxPreset("Low cut", EqSettings(enabled = true, lowCut = 100f)),
        FxPreset("Hangat", EqSettings(enabled = true, lowGain = 3f, highGain = -2f)),
        FxPreset("Jernih", EqSettings(enabled = true, lowCut = 60f, midGain = -2f, midFreq = 400f, highGain = 3f)),
    )

    val stereoizer = listOf(
        FxPreset("Default", StereoizerSettings(enabled = true)),
        FxPreset("Lebar", StereoizerSettings(enabled = true, strength = 0.9f, timbre = 0.6f)),
        FxPreset("Halus", StereoizerSettings(enabled = true, strength = 0.3f, timbre = 0.35f)),
        FxPreset("Haas", StereoizerSettings(enabled = true, strength = 0.2f, lrDelay = 0.6f)),
    )

    val reverb = listOf(
        FxPreset("Default", ReverbSettings(enabled = true)),
        FxPreset("Room", ReverbSettings(enabled = true, mix = 0.2f, decay = 0.8f, preDelay = 0.01f, damping = 0.5f, earlyReflections = 0.5f, roomSize = 0.25f)),
        FxPreset("Hall", ReverbSettings(enabled = true, mix = 0.3f, decay = 3.5f, preDelay = 0.03f, damping = 0.3f, earlyReflections = 0.3f, roomSize = 0.85f)),
        FxPreset("Plate", ReverbSettings(enabled = true, mix = 0.25f, decay = 1.8f, tone = 14_000f, damping = 0.15f, earlyReflections = 0.05f, diffusion = 0.9f)),
        FxPreset("Ambience", ReverbSettings(enabled = true, mix = 0.15f, decay = 1.2f, damping = 0.6f, earlyReflections = 0.6f, roomSize = 0.4f)),
    )

    val graphicEq = listOf(
        FxPreset("Default", GraphicEqSettings(enabled = true)),
        FxPreset("Bass boost", graphic(6f, 4f, 1f, 0f, 0f, 0f, 0f, 0f)),
        FxPreset("Vokal", graphic(-3f, -2f, 0f, 2f, 3f, 2f, 1f, 0f)),
        FxPreset("Terang", graphic(0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f)),
        FxPreset("Loudness", graphic(5f, 3f, 0f, -1f, -1f, 0f, 3f, 4f)),
        FxPreset("Telepon", graphic(-12f, -12f, -6f, 3f, 6f, 3f, -12f, -12f)),
    )

    val leveller = listOf(
        FxPreset("Default", LevellerSettings(enabled = true)),
        FxPreset("+3 dB", LevellerSettings(enabled = true, gainDb = 3f)),
        FxPreset("+6 dB", LevellerSettings(enabled = true, gainDb = 6f)),
        FxPreset("+12 dB", LevellerSettings(enabled = true, gainDb = 12f)),
    )

    val compressor = listOf(
        FxPreset("Default", CompressorSettings(enabled = true)),
        FxPreset("Glue", CompressorSettings(enabled = true, thresholdDb = -14f, ratio = 2f, attackMs = 30f, releaseMs = 200f, makeupDb = 2f)),
        FxPreset("Vokal", CompressorSettings(enabled = true, thresholdDb = -20f, ratio = 3f, attackMs = 5f, releaseMs = 120f, autoMakeup = true)),
        FxPreset("Punch", CompressorSettings(enabled = true, thresholdDb = -16f, ratio = 4f, attackMs = 25f, releaseMs = 80f, kneeDb = 3f, makeupDb = 3f)),
        FxPreset("Squash", CompressorSettings(enabled = true, thresholdDb = -30f, ratio = 10f, attackMs = 1f, releaseMs = 60f, kneeDb = 2f, autoMakeup = true)),
    )

    /** Preset matching current values, ignoring on/off. */
    fun <T> nameOf(presets: List<FxPreset<T>>, current: T, switchedOn: (T) -> T): String =
        presets.firstOrNull { it.settings == switchedOn(current) }?.name ?: CUSTOM

    private fun graphic(vararg gains: Float) = GraphicEqSettings(enabled = true, gains = gains.toList())
}
