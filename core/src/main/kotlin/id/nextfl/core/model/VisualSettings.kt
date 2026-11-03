package id.nextfl.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AspectRatio(val width: Int, val height: Int, val label: String) {
    WIDE(16, 9, "16:9"),
    PORTRAIT(9, 16, "9:16"),
    SQUARE(1, 1, "1:1"),
}

@Serializable
data class VisualSettings(
    val templateId: String = "pulse-ring",
    val aspect: AspectRatio = AspectRatio.WIDE,
    val ringColor: Long = 0xFFFFFFFF,
    val particleColor: Long = 0xFFF3A6FF,
    val title: String = "",
    val sensitivity: Float = 1f,
    val backgroundDim: Float = 0.55f,
    val backgroundBlur: Float = 0f,
    val grayscale: Boolean = true,
    val particleCount: Int = 70,
    val streaks: Boolean = true,
    val shake: Boolean = true,
    val syncMs: Int = 100,
) {
    companion object {
        // Limited by audio render-ahead
        const val MAX_SYNC_MS = 250
    }
}
