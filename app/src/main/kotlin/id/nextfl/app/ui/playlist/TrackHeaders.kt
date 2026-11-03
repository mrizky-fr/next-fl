package id.nextfl.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.Track
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.ColorSwatches
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.LocalControlScale
import id.nextfl.app.ui.components.ModalDialog
import id.nextfl.app.ui.components.TextInput
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import kotlin.math.roundToInt

private const val ROW_DP = 64
private const val ROW_GAP_DP = 1

@Composable
fun TrackHeaders(vm: EditorViewModel, tracks: List<Track>, selectedTrackId: String?) {
    var editing by remember { mutableStateOf<Track?>(null) }
    Column(Modifier.width(132.dp).fillMaxHeight().background(Palette.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).background(Palette.Well).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TRACKS", style = TextStyles.Label, modifier = Modifier.weight(1f))
            Text(
                "+ Track",
                color = Palette.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(Dimens.Radius)).clickable(role = Role.Button, onClick = vm::addTrack).padding(4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            Column(Modifier.offset { IntOffset(0, -vm.viewport.scrollY.roundToInt()) }) {
                tracks.forEach { track ->
                    TrackHeaderRow(
                        track = track,
                        selected = track.id == selectedTrackId,
                        onSelect = { vm.selectTrack(track.id) },
                        onEdit = { editing = track },
                        onMute = { vm.updateTrack(track.id) { it.copy(mute = !it.mute) } },
                        onSolo = { vm.updateTrack(track.id) { it.copy(solo = !it.solo) } },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp).clickable(role = Role.Button, onClick = vm::addTrack).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Palette.TextMuted, modifier = Modifier.size(16.dp))
                    Text("Tambah track", style = TextStyles.Caption)
                }
            }
        }
    }
    editing?.let { track -> TrackEditDialog(vm, track) { editing = null } }
}

// Row height matches timeline rows
@Composable
private fun TrackHeaderRow(track: Track, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onMute: () -> Unit, onSolo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((ROW_DP - ROW_GAP_DP).dp)
            .background(if (selected) Palette.Card else Palette.Surface)
            .combinedClickable(onClick = onSelect, onLongClick = onEdit, onDoubleClick = onEdit)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(Dimens.RadiusSmall)).background(Color(track.color)))
        Column(Modifier.weight(1f)) {
            Text(track.name, style = TextStyles.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Tahan untuk edit", style = TextStyles.Caption.copy(fontSize = 9.sp, color = Palette.TextMuted), maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallToggle("M", track.mute, Palette.Mute, onMute)
            SmallToggle("S", track.solo, Palette.Solo, onSolo)
        }
    }
    Box(Modifier.fillMaxWidth().height(ROW_GAP_DP.dp))
}

@Composable
fun SmallToggle(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    val s = LocalControlScale.current
    Box(
        modifier = Modifier
            .size(width = 24.dp * s, height = 20.dp * s)
            .clip(RoundedCornerShape(Dimens.RadiusSmall))
            .background(if (active) activeColor else Palette.Well)
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Palette.Bg else Palette.TextSecondary, fontSize = 10.sp * s, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TrackEditDialog(vm: EditorViewModel, track: Track, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(track.name) }
    var color by remember { mutableStateOf(track.color) }
    ModalDialog(
        title = "Edit track",
        onDismiss = onDismiss,
        actions = {
            FlatButton("Hapus track", { vm.removeTrack(track.id); onDismiss() }, variant = ButtonVariant.DANGER)
            FlatButton("Simpan", {
                vm.updateTrack(track.id) { it.copy(name = name.trim().ifEmpty { track.name }.take(40), color = color) }
                onDismiss()
            }, variant = ButtonVariant.ACCENT)
        },
    ) {
        Text("Nama", style = TextStyles.Caption)
        TextInput(name, { name = it.take(40) }, Modifier.fillMaxWidth())
        Text("Warna", style = TextStyles.Caption)
        ColorSwatches(Defaults.TRACK_COLORS, color) { color = it }
        Text("Menghapus track juga menghapus semua klip di dalamnya.", style = TextStyles.Caption)
    }
}
