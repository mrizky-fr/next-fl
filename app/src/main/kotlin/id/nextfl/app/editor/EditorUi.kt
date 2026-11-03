package id.nextfl.app.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

enum class AppView(val label: String) { PLAYLIST("Playlist"), MIXER("Mixer"), VISUAL("NextPlayer"), SPLIT("Mode Ngonten") }

enum class SnapMode(val label: String, val beats: Double) {
    BAR("Bar", 4.0),
    BEAT("Beat", 1.0),
    HALF("1/2", 0.5),
    QUARTER("1/4", 0.25),
    OFF("Off", 0.0),
}

data class UiState(
    val view: AppView = AppView.PLAYLIST,
    val snap: SnapMode = SnapMode.BEAT,
    val follow: Boolean = true,
    val libraryOpen: Boolean = false,
    val visualFullscreen: Boolean = false,
)

/** Timeline scroll and zoom. */
@Stable
class TimelineViewport {
    var scrollSeconds by mutableDoubleStateOf(0.0)
    var scrollY by mutableFloatStateOf(0f)
    var pixelsPerSecond by mutableFloatStateOf(0f)

    fun ensureZoom(density: Float) {
        if (pixelsPerSecond == 0f) pixelsPerSecond = INITIAL_DP_PER_SECOND * density
    }

    fun zoomAround(anchorX: Float, factor: Float, density: Float) {
        val anchorTime = scrollSeconds + anchorX / pixelsPerSecond
        pixelsPerSecond = (pixelsPerSecond * factor).coerceIn(MIN_DP_PER_SECOND * density, MAX_DP_PER_SECOND * density)
        scrollSeconds = (anchorTime - anchorX / pixelsPerSecond).coerceAtLeast(0.0)
    }

    companion object {
        const val INITIAL_DP_PER_SECOND = 24f
        const val MIN_DP_PER_SECOND = 2f
        const val MAX_DP_PER_SECOND = 400f
    }
}
