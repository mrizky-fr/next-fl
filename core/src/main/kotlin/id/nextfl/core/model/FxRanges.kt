package id.nextfl.core.model

/** Shared limits for UI and storage. */
object FxRanges {
    val UNIT = 0f..1f
    val BIPOLAR = -1f..1f
    val EQ_GAIN = -18f..18f
    val EQ_LOW_CUT = 20f..1000f
    val EQ_MID_FREQ = 150f..8000f
    val EQ_HIGH_CUT = 1000f..20_000f
    val REVERB_DECAY = 0.3f..8f
    val REVERB_PRE_DELAY = 0f..0.25f
    val REVERB_TONE = 1000f..18_000f
    val REVERB_LOW_CUT = 20f..1000f
    val REVERB_TRIM_DB = -24f..6f
    val GRAPHIC_GAIN = -GraphicEqSettings.MAX_GAIN_DB..GraphicEqSettings.MAX_GAIN_DB
    val LEVELLER_GAIN = 0f..LevellerSettings.MAX_GAIN_DB
    val COMP_THRESHOLD = -60f..0f
    val COMP_RATIO = 1f..20f
    val COMP_ATTACK_MS = 0.1f..100f
    val COMP_RELEASE_MS = 10f..1000f
    val COMP_KNEE = 0f..12f
    val COMP_MAKEUP = -12f..24f
    val PITCH = -Clip.MAX_PITCH_SEMITONES.toFloat()..Clip.MAX_PITCH_SEMITONES.toFloat()
}
