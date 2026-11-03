package id.nextfl.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Uniformly shrinks dp and sp. */
@Composable
fun Scaled(scale: Float, iconScale: Float = 1f, labelScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    // Bar already scaled; buttons keep size
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * scale, density.fontScale),
        LocalControlScale provides 1f,
        LocalIconScale provides iconScale,
        LocalLabelScale provides labelScale,
        content = content,
    )
}
