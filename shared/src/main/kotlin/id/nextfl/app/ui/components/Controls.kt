package id.nextfl.app.ui.components

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

enum class ButtonVariant { GHOST, SOLID, ACCENT, DANGER }

// Every button matches "+ Audio"
const val BUTTON_SCALE = 0.8f
val LocalControlScale = compositionLocalOf { BUTTON_SCALE }
val LocalIconScale = compositionLocalOf { 1.25f }
val LocalLabelScale = compositionLocalOf { 1.15f }

@Composable
fun buttonIconSize() = 18.dp * LocalControlScale.current * LocalIconScale.current

@Composable
fun buttonLabelSize() = 12.sp * LocalControlScale.current * LocalLabelScale.current

private val Shape = RoundedCornerShape(Dimens.Radius)

private fun variantColors(variant: ButtonVariant, active: Boolean): Pair<Color, Color> = when {
    variant == ButtonVariant.ACCENT -> Palette.Accent to Palette.White
    variant == ButtonVariant.DANGER -> Color.Transparent to Palette.Danger
    active -> Palette.AccentSoft to Palette.White
    variant == ButtonVariant.SOLID -> Palette.Control to Palette.White
    else -> Color.Transparent to Palette.TextSecondary
}

@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.GHOST,
    enabled: Boolean = true,
    active: Boolean = false,
    showLabel: Boolean = false,
) {
    val (background, tint) = variantColors(variant, active)
    val s = LocalControlScale.current
    val iconSize = buttonIconSize()
    val labelSize = buttonLabelSize()
    Row(
        modifier = modifier
            .sizeIn(minWidth = Dimens.Control * s, minHeight = Dimens.Control * s)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(Shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 8.dp * s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp * s, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
        if (showLabel) Text(label, color = tint, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    showLabels: Boolean = true,
) {
    val s = LocalControlScale.current
    val iconSize = buttonIconSize()
    val labelSize = buttonLabelSize()
    Row(
        modifier = modifier.clip(Shape).background(Palette.Well).padding(2.dp * s),
        horizontalArrangement = Arrangement.spacedBy(2.dp * s),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tint = if (isSelected) Palette.White else Palette.TextSecondary
            Row(
                modifier = Modifier
                    .height((Dimens.Control - 4.dp) * s)
                    .clip(RoundedCornerShape(Dimens.RadiusSmall))
                    .background(if (isSelected) Palette.Accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 8.dp * s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp * s),
            ) {
                icon?.let { Icon(it(option), contentDescription = label(option), tint = tint, modifier = Modifier.size(iconSize)) }
                if (showLabels || icon == null) {
                    Text(label(option), color = tint, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Switch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val s = LocalControlScale.current
    Box(
        modifier = Modifier
            .size(width = 34.dp * s, height = 18.dp * s)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(Shape)
            .background(if (checked) Palette.Accent else Palette.ControlHigh)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(3.dp * s),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(12.dp * s).clip(RoundedCornerShape(Dimens.RadiusSmall)).background(Palette.White))
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.Body)
            hint?.let { Text(it, style = TextStyles.Caption) }
        }
        Switch(checked, onChange, enabled)
    }
}

val PANEL_PADDING = 10.dp
val PANEL_GAP = 5.dp
private val HEADER_HEIGHT = 14.dp

/** Short row; actions overflow invisibly. */
@Composable
fun PanelHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).wrapContentHeight(unbounded = true),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Borderless card section. */
@Composable
fun PanelSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(Shape).background(Palette.Card).padding(PANEL_PADDING),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        PanelHeader {
            Text(title.uppercase(), style = TextStyles.Label, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
fun LcdBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.clip(Shape).background(Palette.Well).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** Labeled button, icon required. */
@Composable
fun FlatButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.SOLID,
    enabled: Boolean = true,
) {
    val (background, textColor) = when (variant) {
        ButtonVariant.ACCENT -> Palette.Accent to Palette.White
        ButtonVariant.DANGER -> Palette.Danger to Palette.White
        ButtonVariant.SOLID -> Palette.Control to Palette.White
        ButtonVariant.GHOST -> Color.Transparent to Palette.TextSecondary
    }
    val s = LocalControlScale.current
    Row(
        modifier = modifier
            .height(Dimens.Control * s)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(Shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp * s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp * s, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(buttonIconSize()))
        Text(text, color = textColor, fontSize = buttonLabelSize(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ColorSwatches(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(Shape)
                    .background(if (isSelected) Palette.White else Color.Transparent)
                    .padding(if (isSelected) 2.dp else 0.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSmall))
                    .background(Color(value))
                    .clickable(role = Role.RadioButton) { onSelect(value) },
            )
        }
    }
}

@Composable
fun RowScope.ToolbarGap() {
    Box(Modifier.width(10.dp))
}
