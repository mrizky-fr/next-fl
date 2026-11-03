package id.nextfl.app.ui.mixer

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.core.model.Track
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.Fader
import id.nextfl.app.ui.components.Knob
import id.nextfl.app.ui.components.LevelMeter
import id.nextfl.app.ui.playlist.SmallToggle
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles

@Composable
fun MixerScreen(vm: EditorViewModel, state: EditorState) {
    val selected = state.doc.tracks.find { it.id == state.selectedTrackId } ?: state.doc.tracks.firstOrNull()
    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MasterStrip(vm, state.doc.masterDb)
            state.doc.tracks.forEach { track -> ChannelStrip(vm, track, track.id == selected?.id) }
            AddTrackStrip(vm::addTrack)
        }
        if (selected != null) FxPanel(vm, selected)
    }
}

private val STRIP_HEADER = 150.dp
private val STRIP_FOOTER = 22.dp

@Composable
private fun StripFrame(selected: Boolean, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Dimens.Radius)
    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) Palette.Card else Palette.Surface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Tab, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun MasterStrip(vm: EditorViewModel, masterDb: Float) {
    StripFrame(selected = false, onClick = null) {
        Column(Modifier.height(STRIP_HEADER), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MASTER", style = TextStyles.Label.copy(color = Palette.White), modifier = Modifier.padding(top = 4.dp))
        }
        MeterFader(Modifier.weight(1f), { vm.engine.masterPeak }, masterDb, "Volume master") { vm.setMasterDb(it) }
        Box(Modifier.height(STRIP_FOOTER))
    }
}

@Composable
private fun ChannelStrip(vm: EditorViewModel, track: Track, selected: Boolean) {
    StripFrame(selected, onClick = { vm.selectTrack(track.id) }) {
        Column(Modifier.height(STRIP_HEADER), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(Dimens.RadiusSmall)).background(Color(track.color)))
            Text(track.name, style = TextStyles.Body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                FxBadge("EQ", track.eq.enabled && hasEqChange(track))
                FxBadge("REV", track.reverb.enabled && track.reverb.mix > 0f)
            }
            Knob("Pan", track.pan, -1f..1f, 0f, { v -> vm.updateTrack(track.id, "pan:${track.id}") { it.copy(pan = v) } }, ::formatPan, bipolar = true, size = 32.dp)
        }
        MeterFader(Modifier.weight(1f), { vm.engine.trackPeak(track.id) }, track.volumeDb, "Volume ${track.name}") { db ->
            vm.updateTrack(track.id, "vol:${track.id}") { it.copy(volumeDb = db) }
        }
        Row(Modifier.height(STRIP_FOOTER), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallToggle("M", track.mute, Palette.Mute) { vm.updateTrack(track.id) { it.copy(mute = !it.mute) } }
            SmallToggle("S", track.solo, Palette.Solo) { vm.updateTrack(track.id) { it.copy(solo = !it.solo) } }
        }
    }
}

@Composable
private fun MeterFader(modifier: Modifier, peak: () -> Float, db: Float, label: String, onChange: (Float) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val height = maxHeight - 18.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                LevelMeter(peak, height)
                Fader(db, onChange, label, height)
            }
            Text(formatFaderDb(db), style = TextStyles.LcdSmall)
        }
    }
}

@Composable
private fun AddTrackStrip(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(Dimens.Radius))
            .background(Palette.Surface)
            .clickable(role = Role.Button, onClickLabel = "Tambah track", onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun FxBadge(label: String, active: Boolean) {
    Text(
        label,
        color = if (active) Palette.White else Palette.TextMuted,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) Palette.Accent else Palette.Well)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun hasEqChange(track: Track): Boolean = with(track.eq) {
    lowCut > 20.5f || lowGain != 0f || midGain != 0f || highGain != 0f || highCut < 19_900f
}

fun formatPan(pan: Float): String = when {
    kotlin.math.abs(pan) < 0.01f -> "C"
    pan < 0 -> "L${(-pan * 100).toInt()}"
    else -> "R${(pan * 100).toInt()}"
}

private fun formatFaderDb(db: Float): String = if (db <= -60f) "-∞" else "%+.1f".format(db)
