package id.nextfl.app.ui.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nextfl.core.dsp.Equalizer
import id.nextfl.core.model.EqSettings
import id.nextfl.core.model.Track
import id.nextfl.app.AppContainer
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.Knob
import id.nextfl.app.ui.components.KnobScale
import id.nextfl.app.ui.components.PanelSection
import id.nextfl.app.ui.components.Switch
import id.nextfl.app.ui.playlist.TrackEditDialog
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import kotlin.math.ln
import kotlin.math.pow

private const val GRAPH_MIN_HZ = 20.0
private const val GRAPH_MAX_HZ = 20_000.0
private const val GRAPH_RANGE_DB = 18.0
private const val GRAPH_POINTS = 96

@Composable
fun FxPanel(vm: EditorViewModel, track: Track) {
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(track.color)))
            Text(track.name, style = TextStyles.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconAction(Icons.Rounded.Edit, "Edit track", { editing = true })
        }
        EqSection(vm, track)
        ReverbSection(vm, track)
    }
    if (editing) TrackEditDialog(vm, track) { editing = false }
}

@Composable
private fun EqSection(vm: EditorViewModel, track: Track) {
    val eq = track.eq
    fun set(field: String, transform: (EqSettings) -> EqSettings) = vm.setEq(track.id, field, transform)
    PanelSection("Equalizer", action = { Switch(eq.enabled, { on -> set("enabled") { it.copy(enabled = on) } }) }) {
        EqGraph(eq)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Knob("Lo cut", eq.lowCut, 20f..1000f, 20f, { v -> set("lowCut") { it.copy(lowCut = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = eq.enabled)
            Knob("Low", eq.lowGain, -18f..18f, 0f, { v -> set("lowGain") { it.copy(lowGain = v) } }, ::formatGain, bipolar = true, enabled = eq.enabled)
            Knob("Mid", eq.midGain, -18f..18f, 0f, { v -> set("midGain") { it.copy(midGain = v) } }, ::formatGain, bipolar = true, enabled = eq.enabled)
            Knob("Freq", eq.midFreq, 150f..8000f, 1000f, { v -> set("midFreq") { it.copy(midFreq = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = eq.enabled)
            Knob("High", eq.highGain, -18f..18f, 0f, { v -> set("highGain") { it.copy(highGain = v) } }, ::formatGain, bipolar = true, enabled = eq.enabled)
            Knob("Hi cut", eq.highCut, 1000f..20_000f, 20_000f, { v -> set("highCut") { it.copy(highCut = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = eq.enabled)
        }
    }
}

@Composable
private fun ReverbSection(vm: EditorViewModel, track: Track) {
    val reverb = track.reverb
    PanelSection("Reverb", action = { Switch(reverb.enabled, { on -> vm.setReverb(track.id, "enabled") { it.copy(enabled = on) } }) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob("Mix", reverb.mix, 0f..1f, 0.25f, { v -> vm.setReverb(track.id, "mix") { it.copy(mix = v) } }, { "${(it * 100).toInt()}%" }, enabled = reverb.enabled)
            Knob("Decay", reverb.decay, 0.3f..8f, 2.2f, { v -> vm.setReverb(track.id, "decay") { it.copy(decay = v) } }, { "%.1f s".format(it) }, scale = KnobScale.LOG, enabled = reverb.enabled)
            Knob("Pre-dly", reverb.preDelay, 0f..0.25f, 0.02f, { v -> vm.setReverb(track.id, "pre") { it.copy(preDelay = v) } }, { "${(it * 1000).toInt()} ms" }, enabled = reverb.enabled)
            Knob("Tone", reverb.tone, 1000f..18_000f, 9000f, { v -> vm.setReverb(track.id, "tone") { it.copy(tone = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = reverb.enabled)
        }
    }
}

@Composable
private fun EqGraph(eq: EqSettings) {
    val curve = remember(eq) {
        FloatArray(GRAPH_POINTS) { index ->
            val frequency = GRAPH_MIN_HZ * (GRAPH_MAX_HZ / GRAPH_MIN_HZ).pow(index.toDouble() / (GRAPH_POINTS - 1))
            Equalizer.responseDb(eq, frequency, AppContainer.SAMPLE_RATE).toFloat()
        }
    }
    Canvas(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(Dimens.Radius)).background(Palette.Well)) {
        fun xOf(hz: Double) = (ln(hz / GRAPH_MIN_HZ) / ln(GRAPH_MAX_HZ / GRAPH_MIN_HZ)).toFloat() * size.width
        fun yOf(db: Float) = size.height / 2 - (db.coerceIn(-GRAPH_RANGE_DB.toFloat(), GRAPH_RANGE_DB.toFloat()) / GRAPH_RANGE_DB.toFloat()) * size.height / 2
        listOf(100.0, 1000.0, 10_000.0).forEach { hz -> drawLine(Palette.Line, Offset(xOf(hz), 0f), Offset(xOf(hz), size.height), 1f) }
        listOf(-12f, 0f, 12f).forEach { db -> drawLine(if (db == 0f) Palette.ControlHigh else Palette.Line, Offset(0f, yOf(db)), Offset(size.width, yOf(db)), 1f) }
        val line = Path()
        curve.forEachIndexed { index, db ->
            val x = index.toFloat() / (GRAPH_POINTS - 1) * size.width
            if (index == 0) line.moveTo(x, yOf(db)) else line.lineTo(x, yOf(db))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, yOf(0f))
            lineTo(0f, yOf(0f))
            close()
        }
        drawPath(area, Palette.AccentSoft)
        drawPath(line, Palette.Accent, style = Stroke(2.dp.toPx()))
    }
}

fun formatHz(hz: Float): String = if (hz >= 1000f) "%.1fk".format(hz / 1000f) else "${hz.toInt()}"

private fun formatGain(db: Float): String = "%+.1f".format(db)
