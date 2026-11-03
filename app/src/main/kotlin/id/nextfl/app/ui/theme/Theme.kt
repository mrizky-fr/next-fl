package id.nextfl.app.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.app.R

/** Neutral surfaces from #131517. */
object Palette {
    val Bg = Color(0xFF131517)
    val Surface = Color(0xFF1A1C1F)
    val Card = Color(0xFF222428)
    val Control = Color(0xFF2C2F34)
    val ControlHigh = Color(0xFF3A3D43)
    val Well = Color(0xFF0D0E10)
    val Line = Color(0xFF2A2D31)
    val White = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF71717A)
    val Accent = Color(0xFF6366F1)
    val AccentSoft = Color(0x2E6366F1)
    val Danger = Color(0xFFEF4444)
    val Mute = Color(0xFFF59E0B)
    val Solo = Color(0xFF22C55E)
}

object Dimens {
    val TopBarHeight = 56.dp
    val ToolbarHeight = 48.dp
    val Control = 36.dp
    val Radius = 4.dp
    val RadiusSmall = 2.dp
    val Gap = 8.dp
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object TextStyles {
    private const val TABULAR = "tnum"
    val Lcd = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Palette.White, fontFeatureSettings = TABULAR)
    val LcdSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Palette.TextSecondary, fontFeatureSettings = TABULAR)
    val Label = TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = Palette.TextMuted)
    val Body = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = Palette.White)
    val Caption = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = Palette.TextSecondary)
    val Title = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.White)
}

private fun interTypography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    return base.copy(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(),
        titleMedium = base.titleMedium.inter(),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Accent,
        secondary = Palette.Accent,
        background = Palette.Bg,
        surface = Palette.Surface,
        surfaceVariant = Palette.Card,
        onPrimary = Palette.White,
        onBackground = Palette.White,
        onSurface = Palette.White,
        onSurfaceVariant = Palette.TextSecondary,
        outline = Palette.Line,
        error = Palette.Danger,
    )
    val selection = TextSelectionColors(handleColor = Palette.Accent, backgroundColor = Palette.AccentSoft)
    MaterialTheme(colorScheme = scheme, typography = interTypography()) {
        CompositionLocalProvider(LocalTextSelectionColors provides selection, content = content)
    }
}
