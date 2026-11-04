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
}
