package id.nextfl.app.ui.topbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.core.util.TimeFormat
import id.nextfl.app.R
import id.nextfl.app.editor.AppView
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.LcdBox
import id.nextfl.app.ui.components.Scaled
import id.nextfl.app.ui.components.SegmentedControl
import id.nextfl.app.ui.components.rememberFrameTick
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Inter
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

private enum class TopDialog { BPM, EXPORT, PROJECT }

// Compact header: 25% smaller
private const val TOP_BAR_SCALE = 0.75f

@Composable
fun TopBar(vm: EditorViewModel, view: AppView, bpm: Double, canUndo: Boolean, canRedo: Boolean, playing: Boolean) {
    var dialog by remember { mutableStateOf<TopDialog?>(null) }
    Scaled(TOP_BAR_SCALE) {
    Row(
        modifier = Modifier.fillMaxWidth().height(Dimens.TopBarHeight).background(Palette.Surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppMark()
        SegmentedControl(
            options = AppView.entries,
            selected = view,
            label = { it.label },
            icon = { viewIcon(it) },
            onSelect = { selected -> vm.updateUi { it.copy(view = selected) } },
        )
        Spacer(Modifier.weight(1f))
        IconAction(Icons.Rounded.Stop, "Stop", vm::stop, variant = ButtonVariant.SOLID)
        IconAction(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play",
            vm::togglePlay,
            variant = ButtonVariant.ACCENT,
        )
        TimeDisplay(vm, bpm)
        BpmDisplay(bpm) { dialog = TopDialog.BPM }
        Spacer(Modifier.weight(1f))
        IconAction(Icons.AutoMirrored.Rounded.Undo, "Undo", vm::undo, enabled = canUndo)
        IconAction(Icons.AutoMirrored.Rounded.Redo, "Redo", vm::redo, enabled = canRedo)
        IconAction(Icons.Rounded.FileDownload, "Export", { dialog = TopDialog.EXPORT })
        IconAction(Icons.Rounded.MoreVert, "Proyek", { dialog = TopDialog.PROJECT })
    }
    }

    when (dialog) {
        TopDialog.BPM -> BpmDialog(bpm, vm::setBpm) { dialog = null }
        TopDialog.EXPORT -> ExportDialog(onWav = { dialog = null; vm.exportWav() }) { dialog = null }
        TopDialog.PROJECT -> NewProjectDialog(onConfirm = { dialog = null; vm.newProject() }) { dialog = null }
        null -> Unit
    }
}

private fun viewIcon(view: AppView) = when (view) {
    AppView.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
    AppView.MIXER -> Icons.Rounded.Tune
    AppView.VISUAL -> Icons.Rounded.AutoAwesome
    AppView.SPLIT -> Icons.Rounded.VerticalSplit
}

@Composable
private fun AppMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(Dimens.Radius)).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo_glyph), contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(R.string.app_name), color = Palette.White, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimeDisplay(vm: EditorViewModel, bpm: Double) {
    val tick by rememberFrameTick()
    val position = tick.let { vm.position() }
    LcdBox(Modifier.widthIn(min = 112.dp)) {
        Column {
            Text(TimeFormat.clock(position), style = TextStyles.Lcd)
            Row {
                Text("BAR ", style = TextStyles.LcdSmall.copy(color = Palette.TextMuted))
                Text(TimeFormat.barPosition(position, bpm), style = TextStyles.LcdSmall)
            }
        }
    }
}

@Composable
private fun BpmDisplay(bpm: Double, onClick: () -> Unit) {
    LcdBox(Modifier.clip(RoundedCornerShape(Dimens.Radius)).clickable(role = Role.Button, onClickLabel = "Ubah tempo", onClick = onClick)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(EditorViewModel.formatBpm(bpm), style = TextStyles.Lcd)
            Text("BPM", style = TextStyles.LcdSmall.copy(color = Palette.TextMuted))
        }
    }
}
