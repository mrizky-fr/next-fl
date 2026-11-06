package id.nextfl.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.util.TimeFormat
import id.nextfl.app.editor.AppView
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.SnapMode
import id.nextfl.app.editor.UiState
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.Scaled
import id.nextfl.app.ui.components.SegmentedControl
import id.nextfl.app.ui.components.ToolbarGap
import id.nextfl.app.ui.components.buttonIconSize
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

private const val ZOOM_STEP = 1.4f

// Compact toolbar: 20% smaller
private const val BOTTOM_BAR_SCALE = 0.8f

@Composable
fun rememberAudioImporter(vm: EditorViewModel): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.importAudio(context, uris)
    }
    return {
        try {
            picker.launch(arrayOf("audio/*"))
        } catch (missing: ActivityNotFoundException) {
            vm.showError("Tidak ada aplikasi pemilih file")
        }
    }
}

/** Full-width bar on every tab. */
@Composable
fun BottomBar(vm: EditorViewModel, ui: UiState, selectedClipId: String?) {
    val density = LocalDensity.current.density
    val importAudio = rememberAudioImporter(vm)
    val timelineView = ui.view == AppView.PLAYLIST || ui.view == AppView.SPLIT
    Scaled(BOTTOM_BAR_SCALE) {
        Box(Modifier.fillMaxWidth().height(Dimens.ToolbarHeight).background(Palette.Surface).padding(horizontal = 8.dp)) {
            ToolGroup(Modifier.align(Alignment.CenterStart)) {
                IconAction(Icons.Rounded.Add, "Audio", importAudio, variant = ButtonVariant.ACCENT, showLabel = true)
                IconAction(Icons.Rounded.LibraryMusic, "Library", { vm.updateUi { it.copy(libraryOpen = !it.libraryOpen) } }, active = ui.libraryOpen)
                ToolbarGap()
                val hasClip = selectedClipId != null
                IconAction(Icons.Rounded.ContentCut, "Potong di playhead", { selectedClipId?.let(vm::splitClipAtPlayhead) }, enabled = hasClip)
                IconAction(Icons.Rounded.ContentCopy, "Duplikat", { selectedClipId?.let(vm::duplicateClip) }, enabled = hasClip)
                IconAction(Icons.Rounded.Delete, "Hapus klip", { selectedClipId?.let(vm::removeClip) }, enabled = hasClip)
            }
            if (timelineView) {
                ToolGroup(Modifier.align(Alignment.Center)) {
                    Icon(Icons.Rounded.GridOn, contentDescription = "Snap", tint = Palette.TextMuted, modifier = Modifier.size(buttonIconSize()))
                    SegmentedControl(SnapMode.entries, ui.snap, { it.label }, { mode -> vm.updateUi { it.copy(snap = mode) } })
                }
                ToolGroup(Modifier.align(Alignment.CenterEnd)) {
                    IconAction(Icons.Rounded.ZoomOut, "Perkecil", { vm.viewport.zoomAround(0f, 1 / ZOOM_STEP, density) })
                    IconAction(Icons.Rounded.ZoomIn, "Perbesar", { vm.viewport.zoomAround(0f, ZOOM_STEP, density) })
                    IconAction(Icons.Rounded.MyLocation, "Ikuti playhead", { vm.updateUi { it.copy(follow = !it.follow) } }, active = ui.follow)
                }
            }
        }
    }
}

@Composable
private fun ToolGroup(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
fun LibraryPanel(vm: EditorViewModel, assets: List<AudioAsset>) {
    val importAudio = rememberAudioImporter(vm)
    Column(
        modifier = Modifier
            .padding(start = 140.dp, top = 30.dp, bottom = 6.dp)
            .width(300.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(Dimens.Radius))
            .background(Palette.Card),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LIBRARY AUDIO", style = TextStyles.Label, modifier = Modifier.weight(1f))
            IconAction(Icons.Rounded.Close, "Tutup", { vm.updateUi { it.copy(libraryOpen = false) } })
        }
        if (assets.isEmpty()) {
            Text("Belum ada audio. Import lagu untuk mulai.", style = TextStyles.Caption, modifier = Modifier.padding(12.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(assets, key = { it.id }) { asset -> LibraryRow(asset, { vm.placeAsset(asset.id) }, { vm.removeAsset(asset.id) }) }
        }
        FlatButton("Import audio", Icons.Rounded.UploadFile, importAudio, Modifier.fillMaxWidth().padding(10.dp), variant = ButtonVariant.ACCENT)
    }
}

@Composable
private fun LibraryRow(asset: AudioAsset, onAdd: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(asset.name, style = TextStyles.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bpm = asset.bpm?.let { " · ${EditorViewModel.formatBpm(it)} BPM" }.orEmpty()
            Text("${TimeFormat.duration(asset.duration)}$bpm", style = TextStyles.Caption)
        }
        IconAction(Icons.Rounded.Add, "Tambah ke playlist", onAdd)
        IconAction(Icons.Rounded.Delete, "Hapus dari library", onDelete, variant = ButtonVariant.DANGER)
    }
}
