package id.nextfl.app.ui.topbar

import id.nextfl.core.model.AspectRatio
import id.nextfl.app.video.VideoPlan
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.Audiotrack
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
import androidx.compose.runtime.mutableIntStateOf
import id.nextfl.core.pcm.Mp3Writer
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
private val RATIO_DIALOG_WIDTH = 360.dp
private val RATIO_FRAME = 44.dp
private const val NOMINAL_SHORT_SIDE = 1080

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
fun ExportDialog(onWav: () -> Unit, onMp3: (Int) -> Unit, onVideo: (AspectRatio) -> Unit, onDismiss: () -> Unit) {
    var choosingRatio by remember { mutableStateOf(false) }
    var choosingBitrate by remember { mutableStateOf(false) }
    if (choosingRatio) {
        VideoRatioDialog(onBack = { choosingRatio = false }, onRender = onVideo, onDismiss = onDismiss)
        return
    }
    if (choosingBitrate) {
        Mp3BitrateDialog(onBack = { choosingBitrate = false }, onRender = onMp3, onDismiss = onDismiss)
        return
    }
    ModalDialog("Export", onDismiss) {
        ExportOption(Icons.Rounded.GraphicEq, "Audio WAV", "Mixdown semua track dan efek, 16-bit stereo 44.1 kHz.", onWav)
        ExportOption(Icons.Rounded.Audiotrack, "Audio MP3", "Mixdown stereo 128 sampai 320 kbps, ringan untuk dibagikan.") { choosingBitrate = true }
        ExportOption(Icons.Rounded.Movie, "Video MP4", "Template NextPlayer bersama audio, 30 fps.") { choosingRatio = true }
    }
}

// Render stays locked until picked
@Composable
private fun VideoRatioDialog(onBack: () -> Unit, onRender: (AspectRatio) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<AspectRatio?>(null) }
    ModalDialog(
        title = "Pilih rasio video",
        onDismiss = onDismiss,
        width = RATIO_DIALOG_WIDTH,
        actions = {
            FlatButton("Kembali", Icons.AutoMirrored.Rounded.ArrowBack, onBack, variant = ButtonVariant.GHOST)
            FlatButton("Render", Icons.Rounded.Movie, { selected?.let(onRender) }, variant = ButtonVariant.ACCENT, enabled = selected != null)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectRatio.entries.forEach { aspect ->
                RatioOption(aspect, aspect == selected, Modifier.weight(1f)) { selected = aspect }
            }
        }
    }
}

@Composable
private fun RatioOption(aspect: AspectRatio, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (width, height) = VideoPlan.frameSize(aspect, NOMINAL_SHORT_SIDE)
    val ratio = aspect.width.toFloat() / aspect.height
    Column(
        modifier = modifier
            .clip(Shape)
            .background(if (selected) Palette.Accent else Palette.Card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(RATIO_FRAME), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(if (ratio >= 1) RATIO_FRAME else RATIO_FRAME * ratio, if (ratio >= 1) RATIO_FRAME / ratio else RATIO_FRAME)
                    .clip(RoundedCornerShape(Dimens.RadiusSmall))
                    .background(if (selected) Palette.White else Palette.ControlHigh),
            )
        }
        Text(aspect.label, style = TextStyles.Title.copy(fontSize = 13.sp))
        Text("$width×$height", style = TextStyles.Caption.copy(color = if (selected) Palette.White else Palette.TextSecondary))
    }
}

// Best quality preselected
@Composable
private fun Mp3BitrateDialog(onBack: () -> Unit, onRender: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(Mp3Writer.DEFAULT_BITRATE_KBPS) }
    ModalDialog(
        title = "Pilih kualitas MP3",
        onDismiss = onDismiss,
        width = RATIO_DIALOG_WIDTH,
        actions = {
            FlatButton("Kembali", Icons.AutoMirrored.Rounded.ArrowBack, onBack, variant = ButtonVariant.GHOST)
            FlatButton("Render", Icons.Rounded.Audiotrack, { onRender(selected) }, variant = ButtonVariant.ACCENT)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mp3Writer.BITRATES_KBPS.forEach { bitrate ->
                BitrateOption(bitrate, bitrate == selected, Modifier.weight(1f)) { selected = bitrate }
            }
        }
    }
}

@Composable
private fun BitrateOption(bitrate: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val detail = TextStyles.Caption.copy(color = if (selected) Palette.White else Palette.TextSecondary)
    Column(
        modifier = modifier
            .clip(Shape)
            .background(if (selected) Palette.Accent else Palette.Card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$bitrate", style = TextStyles.Title.copy(fontSize = 15.sp))
        Text("kbps", style = detail)
        Text(megabytesPerMinute(bitrate), style = detail)
    }
}

private fun megabytesPerMinute(bitrateKbps: Int): String =
    String.format(INDONESIAN, "±%.1f MB/menit", bitrateKbps * BITS_PER_KILOBIT * SECONDS_PER_MINUTE / BITS_PER_BYTE / BYTES_PER_MEGABYTE)

private val INDONESIAN: Locale = Locale.forLanguageTag("id")
private const val BITS_PER_KILOBIT = 1000.0
private const val SECONDS_PER_MINUTE = 60.0
private const val BITS_PER_BYTE = 8.0
private const val BYTES_PER_MEGABYTE = 1_000_000.0

@Composable
private fun ExportOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape)
            .background(Palette.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(Shape).background(Palette.Accent), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.Title.copy(fontSize = 13.sp))
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
