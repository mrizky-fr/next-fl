package id.nextfl.app.ui.playlist

import id.nextfl.app.ui.components.PanelHeader
import id.nextfl.app.ui.components.PANEL_PADDING
import id.nextfl.app.ui.components.PANEL_GAP
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Clip
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.Knob
import id.nextfl.app.ui.components.ModalDialog
import id.nextfl.app.ui.components.PanelSection
import id.nextfl.app.ui.components.TextInput
import id.nextfl.app.ui.components.ToggleRow
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

@Composable
fun ClipInspector(vm: EditorViewModel, state: EditorState, clip: Clip) {
    val asset = state.assets[clip.assetId]
    val span = clipSpan(clip, state.assets, state.doc.bpm)
    Row(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.width(272.dp).fillMaxHeight().background(Palette.Surface).verticalScroll(rememberScrollState()).padding(PANEL_PADDING),
            verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
        ) {
            PanelHeader {
                Text(clip.name, style = TextStyles.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconAction(Icons.Rounded.Close, "Tutup", { vm.selectClip(null) })
            }
            ClipLevelSection(vm, clip, span.duration)
            TempoSection(vm, clip, asset, span.rate, state.doc.bpm)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction(Icons.Rounded.ContentCut, "Potong", { vm.splitClipAtPlayhead(clip.id) }, variant = ButtonVariant.SOLID, showLabel = true)
                IconAction(Icons.Rounded.ContentCopy, "Duplikat", { vm.duplicateClip(clip.id) }, variant = ButtonVariant.SOLID, showLabel = true)
                IconAction(Icons.Rounded.Delete, "Hapus", { vm.removeClip(clip.id) }, variant = ButtonVariant.DANGER)
            }
        }
    }
}

@Composable
private fun ClipLevelSection(vm: EditorViewModel, clip: Clip, duration: Double) {
    val maxFade = (duration / 2).coerceAtLeast(0.01).toFloat()
    PanelSection("Klip", action = { FlatButton("Normalize", Icons.Rounded.AutoFixHigh, { vm.normalizeClip(clip.id) }, variant = ButtonVariant.GHOST) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Knob("Gain", clip.gainDb, -24f..24f, 0f, { v -> vm.updateClip(clip.id, "gain:${clip.id}") { it.copy(gainDb = v) } }, { formatDb(it) }, bipolar = true)
            Knob("Fade in", clip.fadeIn.toFloat().coerceAtMost(maxFade), 0f..maxFade, 0f, { v -> vm.updateClip(clip.id, "fi:${clip.id}") { it.copy(fadeIn = v.toDouble()) } }, { formatSeconds(it) })
            Knob("Fade out", clip.fadeOut.toFloat().coerceAtMost(maxFade), 0f..maxFade, 0f, { v -> vm.updateClip(clip.id, "fo:${clip.id}") { it.copy(fadeOut = v.toDouble()) } }, { formatSeconds(it) })
        }
    }
}

@Composable
private fun TempoSection(vm: EditorViewModel, clip: Clip, asset: AudioAsset?, rate: Double, projectBpm: Double) {
    var editingBpm by remember { mutableStateOf(false) }
    val sourceBpm = asset?.bpm
    val bpmLabel = sourceBpm?.let { "${EditorViewModel.formatBpm(it)} BPM" } ?: "BPM"
    PanelSection("Tempo", action = { FlatButton(bpmLabel, Icons.Rounded.Speed, { editingBpm = true }, variant = ButtonVariant.GHOST, enabled = asset != null) }) {
        ToggleRow(
            label = "Ikuti BPM project",
            checked = clip.syncTempo,
            enabled = sourceBpm != null,
            onChange = { checked -> vm.updateClip(clip.id) { it.copy(syncTempo = checked) } },
        )
        if (!clip.syncTempo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Knob("Speed", clip.speed.toFloat(), 0.5f..2f, 1f, { v ->
                    vm.updateClip(clip.id, "speed:${clip.id}") { it.copy(speed = Math.round(v * 100) / 100.0) }
                }, { "×%.2f".format(it) })
            }
        }
        ToggleRow(
            label = "Kunci nada",
            checked = clip.keepPitch,
            onChange = { checked -> vm.updateClip(clip.id) { it.copy(keepPitch = checked) } },
        )
    }
    if (editingBpm && asset != null) AssetBpmDialog(asset, onSave = { vm.setAssetBpm(asset.id, it) }) { editingBpm = false }
}

@Composable
private fun AssetBpmDialog(asset: AudioAsset, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(asset.bpm?.let(EditorViewModel::formatBpm).orEmpty()) }
    val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 40.0..300.0 }
    ModalDialog(
        title = "BPM asli lagu",
        onDismiss = onDismiss,
        width = 280.dp,
        actions = {
            FlatButton("Batal", Icons.Rounded.Close, onDismiss, variant = ButtonVariant.GHOST)
            FlatButton("Simpan", Icons.Rounded.Check, { onSave(parsed); onDismiss() }, variant = ButtonVariant.ACCENT, enabled = parsed != null)
        },
    ) {
        Text("Koreksi kalau deteksi otomatis salah (misal setengah atau dua kali lipat).", style = TextStyles.Caption)
        TextInput(text, { text = it.take(6) }, Modifier.fillMaxWidth(), placeholder = "contoh 128", numeric = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            asset.bpm?.let { bpm ->
                FlatButton("÷2 (${EditorViewModel.formatBpm(bpm / 2)})", Icons.Rounded.Remove, { text = EditorViewModel.formatBpm(bpm / 2) })
                FlatButton("×2 (${EditorViewModel.formatBpm(bpm * 2)})", Icons.Rounded.Add, { text = EditorViewModel.formatBpm(bpm * 2) })
            }
        }
    }
}

fun formatDb(db: Float): String = when {
    db <= -60f -> "-∞"
    else -> "%+.1f dB".format(db)
}

fun formatSeconds(seconds: Float): String = if (seconds < 1f) "${(seconds * 1000).toInt()} ms" else "%.2f s".format(seconds)
