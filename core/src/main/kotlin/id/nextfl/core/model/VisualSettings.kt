package id.nextfl.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    WIDE(16, 9, "16:9"),
    PORTRAIT(9, 16, "9:16"),
    SQUARE(1, 1, "1:1"),
}

@Serializable
enum class SocialPlatform(val label: String) {
    TIKTOK("TikTok"),
    YOUTUBE("YouTube"),
    SPOTIFY("Spotify"),
    INSTAGRAM("Instagram"),
}

@Serializable
data class VisualSettings(
    val templateId: String = "pulse-ring",
    val aspect: AspectRatio = AspectRatio.WIDE,
    val ringColor: Long = WHITE,
    val particleColor: Long = WHITE,
    val title: String = "",
    val sensitivity: Float = 1f,
    val backgroundDim: Float = 0.55f,
    val backgroundBlur: Float = 0f,
    val grayscale: Boolean = true,
    val particleCount: Int = 70,
    val streaks: Boolean = true,
    val cometCount: Int = 6,
    val shake: Boolean = true,
    val syncMs: Int = 100,
    val showLogo: Boolean = true,
    val showHills: Boolean = true,
    val rippleAmount: Float = 0.5f,
    val pendulumAmount: Float = 0.3f,
    val glitchAmount: Float = 0.25f,
    val cometGlare: Float = DEFAULT_GLARE,
    val glitchGlare: Float = DEFAULT_GLARE,
    val hillGlare: Float = DEFAULT_GLARE,
    val beatBass: Float = 1f,
    val beatMid: Float = 0f,
    val beatHigh: Float = 0f,
    val showSocial: Boolean = false,
    val socialUsername: String = "",
    val socialPlatforms: Set<SocialPlatform> = SocialPlatform.entries.toSet(),
) {
    companion object {
        const val WHITE = 0xFFFFFFFF

        // Old default, now white
        const val LEGACY_PARTICLE = 0xFFF3A6FF

        // Limited by audio render-ahead
        const val MAX_SYNC_MS = 250

        // Each comet has heavy trail
        const val MAX_COMETS = 16
        const val MAX_USERNAME = 40
        const val DEFAULT_GLARE = 0.5f
    }
}
