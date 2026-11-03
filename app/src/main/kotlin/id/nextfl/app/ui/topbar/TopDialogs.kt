package id.nextfl.app.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.ModalDialog
import id.nextfl.app.ui.components.TextInput
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

private const val TAP_RESET_MS = 2000L
private const val MAX_TAPS = 8
private val Shape = RoundedCornerShape(Dimens.Radius)

@Composable
fun BpmDialog(bpm: Double, onChange: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(EditorViewModel.formatBpm(bpm)) }
    LaunchedEffect(bpm) {
        if (text.toDoubleOrNull() != bpm) text = EditorViewModel.formatBpm(bpm)
    }
    val taps = remember { mutableStateListOf<Long>() }
    ModalDialog("Tempo project", onDismiss, width = 280.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconAction(Icons.Rounded.Remove, "Kurangi", { onChange(bpm - 1) }, variant = ButtonVariant.SOLID)
            TextInput(
                value = text,
                numeric = true,
                onChange = { input ->
                    text = input
                    input.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 40 }?.let(onChange)
                },
                modifier = Modifier.weight(1f),
            )
            IconAction(Icons.Rounded.Add, "Tambah", { onChange(bpm + 1) }, variant = ButtonVariant.SOLID)
        }
        FlatButton("TAP TEMPO", Icons.Rounded.TouchApp, variant = ButtonVariant.ACCENT, modifier = Modifier.fillMaxWidth(), onClick = {
            val now = System.currentTimeMillis()
            if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
            taps.add(now)
            while (taps.size > MAX_TAPS) taps.removeAt(0)
            if (taps.size >= 2) onChange(60_000.0 / ((taps.last() - taps.first()).toDouble() / (taps.size - 1)))
        })
        Text("Ketuk mengikuti ketukan lagu untuk mengisi BPM.", style = TextStyles.Caption)
    }
}

@Composable
fun ExportDialog(onWav: () -> Unit, onDismiss: () -> Unit) {
    ModalDialog("Export", onDismiss) {
        ExportOption(Icons.Rounded.GraphicEq, "Audio WAV", "Mixdown semua track dan efek, 16-bit stereo 44.1 kHz.", onWav)
        ExportOption(Icons.Rounded.Movie, "Video MP4", "Render template visual bersama audio.", null, badge = "Segera")
    }
}

@Composable
private fun ExportOption(icon: ImageVector, title: String, description: String, onClick: (() -> Unit)?, badge: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (onClick == null) 0.5f else 1f)
            .clip(Shape)
            .background(Palette.Card)
            .clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(Shape).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = TextStyles.Title.copy(fontSize = 13.sp))
                badge?.let {
                    Text(
                        it.uppercase(),
                        color = Palette.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(Shape).background(Palette.ControlHigh).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(description, style = TextStyles.Caption)
        }
    }
}

@Composable
fun NewProjectDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalDialog(
        title = "Proyek baru",
        onDismiss = onDismiss,
        actions = {
            FlatButton("Batal", Icons.Rounded.Close, onDismiss, variant = ButtonVariant.GHOST)
            FlatButton("Mulai baru", Icons.Rounded.RestartAlt, onConfirm, variant = ButtonVariant.DANGER)
        },
    ) {
        Text(
            "Semua track, klip, dan audio di proyek ini akan dihapus. Logo dan background template tetap disimpan.",
            style = TextStyles.Body.copy(color = Palette.TextSecondary),
        )
    }
}
