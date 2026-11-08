package id.nextfl.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** NextFL mark, same as launcher. */
val LogoGlyph: ImageVector by lazy {
    ImageVector.Builder(name = "LogoGlyph", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 472f, viewportHeight = 472f)
        .apply {
            group(translationX = -132f, translationY = -132f) {
                LOGO_PATHS.forEach { data -> addPath(addPathNodes(data), fill = SolidColor(Color.White)) }
            }
        }
        .build()
}

private val LOGO_PATHS = listOf(
    "M147,460a115,115 0,1 0,230 0a115,115 0,1 0,-230 0z",
    "M292.5,202.5a42.5,42.5 0,0 1,85 0L377.5,460L292.5,460z",
    "M398,272.5a42.5,42.5 0,0 1,85 0L483,427.5a42.5,42.5 0,0 1,-85 0z",
    "M503.5,327.5a42.5,42.5 0,0 1,85 0L588.5,372.5a42.5,42.5 0,0 1,-85 0z",
)
