package id.nextfl.app.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.PANEL_GAP
import id.nextfl.app.ui.components.PANEL_PADDING
import id.nextfl.app.ui.components.Switch
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import id.nextfl.core.model.FxPreset
import id.nextfl.core.model.FxPresets

private val Shape = RoundedCornerShape(Dimens.Radius)

/** Null key means own undo step. */
typealias ModuleSetter<T> = (key: String?, transform: (T) -> T) -> Unit

/** Describes one module's on/off and presets. */
class ModuleSpec<T>(
    val title: String,
    val presets: List<FxPreset<T>>,
    val isEnabled: (T) -> Boolean,
    val switched: (T, Boolean) -> T,
)

@Composable
fun <T : Any> FxModule(
    spec: ModuleSpec<T>,
    settings: T,
    pasted: T?,
    onCopy: (Any) -> Unit,
    set: ModuleSetter<T>,
    content: @Composable ColumnScope.(enabled: Boolean) -> Unit,
) {
    val enabled = spec.isEnabled(settings)
    val presetName = FxPresets.nameOf(spec.presets, settings) { spec.switched(it, true) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(Shape).background(Palette.Card).padding(PANEL_PADDING),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(enabled, { on -> set(null) { spec.switched(it, on) } })
            Text(spec.title, style = TextStyles.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PresetButton(presetName, spec.presets) { preset -> set(null) { preset.settings } }
            ModuleMenu(
                onReset = { set(null) { spec.switched(spec.presets.first().settings, enabled) } },
                onCopy = { onCopy(settings) },
                onPaste = pasted?.let { value -> { set(null) { value } } },
            )
        }
        content(enabled)
    }
}

@Composable
private fun <T> PresetButton(name: String, presets: List<FxPreset<T>>, onSelect: (FxPreset<T>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clip(Shape).clickable(role = Role.Button) { open = true }.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = TextStyles.Body.copy(color = Palette.TextSecondary))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pilih preset", tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
        }
        FlatMenu(open, { open = false }) {
            presets.forEach { preset ->
                val current = preset.name == name
                MenuItem(preset.name, if (current) Icons.Rounded.Check else Icons.Rounded.Tune, highlight = current) {
                    open = false
                    onSelect(preset)
                }
            }
        }
    }
}

@Composable
private fun ModuleMenu(onReset: () -> Unit, onCopy: () -> Unit, onPaste: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Icons.Rounded.Menu, "Menu modul", { open = true })
        FlatMenu(open, { open = false }) {
            MenuItem("Reset", Icons.Rounded.RestartAlt) { open = false; onReset() }
            MenuItem("Salin", Icons.Rounded.ContentCopy) { open = false; onCopy() }
            MenuItem("Tempel", Icons.Rounded.ContentPaste, enabled = onPaste != null) { open = false; onPaste?.invoke() }
        }
    }
}

// Borderless, no shadow
@Composable
private fun FlatMenu(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = Shape,
        containerColor = Palette.Control,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, highlight: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        // Unspecified lets menu colors apply
        text = { Text(label, style = TextStyles.Body.copy(color = Color.Unspecified)) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = MenuDefaults.itemColors(
            textColor = Palette.White,
            leadingIconColor = if (highlight) Palette.AccentLight else Palette.TextSecondary,
            disabledTextColor = Palette.TextMuted,
            disabledLeadingIconColor = Palette.TextMuted,
        ),
    )
}
