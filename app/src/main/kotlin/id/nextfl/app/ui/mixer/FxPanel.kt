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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.nextfl.app.AppContainer
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.FxTarget
import id.nextfl.app.ui.components.HorizontalMeter
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.Knob
import id.nextfl.app.ui.components.KnobScale
import id.nextfl.app.ui.components.PANEL_GAP
import id.nextfl.app.ui.components.PANEL_PADDING
import id.nextfl.app.ui.components.PanelHeader
import id.nextfl.app.ui.components.SegmentedControl
import id.nextfl.app.ui.components.ToggleRow
import id.nextfl.app.ui.components.ValueSlider
import id.nextfl.app.ui.components.VerticalSlider
import id.nextfl.app.ui.components.meterRatio
import id.nextfl.app.ui.playlist.TrackEditDialog
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import id.nextfl.core.dsp.Compressor
import id.nextfl.core.dsp.Equalizer
import id.nextfl.core.dsp.Stereoizer
import id.nextfl.core.model.CompressorSettings
import id.nextfl.core.model.EqSettings
import id.nextfl.core.model.FxChain
import id.nextfl.core.model.FxPresets
import id.nextfl.core.model.FxRanges
import id.nextfl.core.model.GraphicEqSettings
import id.nextfl.core.model.LevellerSettings
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.model.StereoizerSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val GRAPH_MIN_HZ = 20.0
private const val GRAPH_MAX_HZ = 20_000.0
private const val GRAPH_RANGE_DB = 18.0
private const val GRAPH_POINTS = 96
private val PANEL_WIDTH = 380.dp
private val GRAPHIC_SLIDER_HEIGHT = 120.dp
private val GRAPHIC_SLIDER_WIDTH = 28.dp
private val COMP_GRAPH_HEIGHT = 72.dp
private const val COMP_GRAPH_FLOOR_DB = -60f
private const val RATIO_WHOLE_FROM = 10f
private const val MS_WHOLE_FROM = 10f
private val METER_LABELS = listOf(-48f to "-∞", -36f to "-36", -24f to "-24", -12f to "-12", -6f to "-6", 0f to "0", 3f to "+3")

private val EQ_SPEC = ModuleSpec<EqSettings>("Parametric EQ", FxPresets.eq, { it.enabled }, { s, on -> s.copy(enabled = on) })
private val COMP_SPEC = ModuleSpec<CompressorSettings>("Compressor", FxPresets.compressor, { it.enabled }, { s, on -> s.copy(enabled = on) })
private val STEREOIZER_SPEC = ModuleSpec<StereoizerSettings>("Stereoizer", FxPresets.stereoizer, { it.enabled }, { s, on -> s.copy(enabled = on) })
private val REVERB_SPEC = ModuleSpec<ReverbSettings>("Reverb", FxPresets.reverb, { it.enabled }, { s, on -> s.copy(enabled = on) })
private val GRAPHIC_SPEC = ModuleSpec<GraphicEqSettings>("Graphic EQ", FxPresets.graphicEq, { it.enabled }, { s, on -> s.copy(enabled = on) })
private val LEVELLER_SPEC = ModuleSpec<LevellerSettings>("Leveller", FxPresets.leveller, { it.enabled }, { s, on -> s.copy(enabled = on) })

private enum class ReverbTab(val label: String) { MAIN("Main"), TUNE("Tune"), MIXER("Mixer") }

/** Insert chain of selected channel. */
@Composable
fun FxPanel(vm: EditorViewModel, doc: ProjectDoc, target: FxTarget) {
    val track = (target as? FxTarget.Channel)?.let { channel -> doc.tracks.find { it.id == channel.trackId } }
    if (target is FxTarget.Channel && track == null) return
    val chain = track?.fx ?: doc.masterFx
    val level: () -> Float = if (track != null) ({ vm.trackLevel(track.id) }) else vm::masterLevel
    val clipboard = vm.fxClipboard
    fun update(key: String?, transform: (FxChain) -> FxChain) = vm.updateFx(target, key, transform)
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(PANEL_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(PANEL_PADDING),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        PanelHeader {
            if (track != null) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(track.color)))
                Text(track.name, style = TextStyles.Title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconAction(Icons.Rounded.Edit, "Edit track", { editing = true })
            } else {
                Text("MASTER", style = TextStyles.Title, modifier = Modifier.weight(1f))
            }
        }
        FxModule(EQ_SPEC, chain.eq, clipboard as? EqSettings, vm::copyFx, { key, t -> update(key?.let { "eq:$it" }) { it.copy(eq = t(it.eq)) } }) { on ->
            EqControls(chain.eq, on) { key, t -> update("eq:$key") { it.copy(eq = t(it.eq)) } }
        }
        FxModule(COMP_SPEC, chain.compressor, clipboard as? CompressorSettings, vm::copyFx, { key, t -> update(key?.let { "cmp:$it" }) { it.copy(compressor = t(it.compressor)) } }) { on ->
            CompressorControls(chain.compressor, on) { key, t -> update("cmp:$key") { it.copy(compressor = t(it.compressor)) } }
        }
        FxModule(STEREOIZER_SPEC, chain.stereoizer, clipboard as? StereoizerSettings, vm::copyFx, { key, t -> update(key?.let { "st:$it" }) { it.copy(stereoizer = t(it.stereoizer)) } }) { on ->
            StereoizerControls(chain.stereoizer, on) { key, t -> update("st:$key") { it.copy(stereoizer = t(it.stereoizer)) } }
        }
        FxModule(REVERB_SPEC, chain.reverb, clipboard as? ReverbSettings, vm::copyFx, { key, t -> update(key?.let { "rev:$it" }) { it.copy(reverb = t(it.reverb)) } }) { on ->
            ReverbControls(chain.reverb, on) { key, t -> update("rev:$key") { it.copy(reverb = t(it.reverb)) } }
        }
        FxModule(GRAPHIC_SPEC, chain.graphicEq, clipboard as? GraphicEqSettings, vm::copyFx, { key, t -> update(key?.let { "geq:$it" }) { it.copy(graphicEq = t(it.graphicEq)) } }) { on ->
            GraphicEqControls(chain.graphicEq, on) { key, t -> update("geq:$key") { it.copy(graphicEq = t(it.graphicEq)) } }
        }
        FxModule(LEVELLER_SPEC, chain.leveller, clipboard as? LevellerSettings, vm::copyFx, { key, t -> update(key?.let { "lvl:$it" }) { it.copy(leveller = t(it.leveller)) } }) { on ->
            LevellerControls(chain.leveller, on, level) { key, t -> update("lvl:$key") { it.copy(leveller = t(it.leveller)) } }
        }
    }
    if (editing && track != null) TrackEditDialog(vm, track) { editing = false }
}

/** Drag edits merge per field. */
private typealias FieldSetter<T> = (key: String, transform: (T) -> T) -> Unit

@Composable
private fun EqControls(eq: EqSettings, on: Boolean, set: FieldSetter<EqSettings>) {
    EqGraph(eq)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Knob("Lo cut", eq.lowCut, FxRanges.EQ_LOW_CUT, 20f, { v -> set("lowCut") { it.copy(lowCut = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = on)
        Knob("Low", eq.lowGain, FxRanges.EQ_GAIN, 0f, { v -> set("lowGain") { it.copy(lowGain = v) } }, ::formatGain, bipolar = true, enabled = on)
        Knob("Mid", eq.midGain, FxRanges.EQ_GAIN, 0f, { v -> set("midGain") { it.copy(midGain = v) } }, ::formatGain, bipolar = true, enabled = on)
        Knob("Freq", eq.midFreq, FxRanges.EQ_MID_FREQ, 1000f, { v -> set("midFreq") { it.copy(midFreq = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = on)
        Knob("High", eq.highGain, FxRanges.EQ_GAIN, 0f, { v -> set("highGain") { it.copy(highGain = v) } }, ::formatGain, bipolar = true, enabled = on)
        Knob("Hi cut", eq.highCut, FxRanges.EQ_HIGH_CUT, 20_000f, { v -> set("highCut") { it.copy(highCut = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = on)
    }
}

@Composable
private fun CompressorControls(comp: CompressorSettings, on: Boolean, set: FieldSetter<CompressorSettings>) {
    val defaults = CompressorSettings()
    CompressorGraph(comp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Knob("Threshold", comp.thresholdDb, FxRanges.COMP_THRESHOLD, defaults.thresholdDb, { v -> set("threshold") { it.copy(thresholdDb = v) } }, ::formatDecibels, enabled = on)
        Knob("Ratio", comp.ratio, FxRanges.COMP_RATIO, defaults.ratio, { v -> set("ratio") { it.copy(ratio = v) } }, ::formatRatio, scale = KnobScale.LOG, enabled = on)
        Knob("Attack", comp.attackMs, FxRanges.COMP_ATTACK_MS, defaults.attackMs, { v -> set("attack") { it.copy(attackMs = v) } }, ::formatMs, scale = KnobScale.LOG, enabled = on)
        Knob("Release", comp.releaseMs, FxRanges.COMP_RELEASE_MS, defaults.releaseMs, { v -> set("release") { it.copy(releaseMs = v) } }, ::formatMs, scale = KnobScale.LOG, enabled = on)
        Knob("Knee", comp.kneeDb, FxRanges.COMP_KNEE, defaults.kneeDb, { v -> set("knee") { it.copy(kneeDb = v) } }, ::formatDecibels, enabled = on)
        Knob("Gain", comp.makeupDb, FxRanges.COMP_MAKEUP, defaults.makeupDb, { v -> set("makeup") { it.copy(makeupDb = v) } }, ::formatGain, enabled = on)
    }
    ToggleRow("Gain otomatis", comp.autoMakeup, { v -> set("auto") { it.copy(autoMakeup = v) } }, enabled = on)
}

// Input to output level
@Composable
private fun CompressorGraph(comp: CompressorSettings) {
    val curve = remember(comp) {
        FloatArray(GRAPH_POINTS) { index ->
            val inputDb = COMP_GRAPH_FLOOR_DB * (1f - index.toFloat() / (GRAPH_POINTS - 1))
            Compressor.outputDb(comp, inputDb)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(COMP_GRAPH_HEIGHT).clip(RoundedCornerShape(Dimens.Radius)).background(Palette.Well)) {
        fun yOf(db: Float) = db.coerceIn(COMP_GRAPH_FLOOR_DB, 0f) / COMP_GRAPH_FLOOR_DB * size.height
        val thresholdX = (1f - comp.thresholdDb / COMP_GRAPH_FLOOR_DB) * size.width
        drawLine(Palette.Line, Offset(0f, size.height), Offset(size.width, 0f), 1f)
        drawLine(Palette.ControlHigh, Offset(thresholdX, 0f), Offset(thresholdX, size.height), 1f)
        val line = Path()
        curve.forEachIndexed { index, db ->
            val x = index.toFloat() / (GRAPH_POINTS - 1) * size.width
            if (index == 0) line.moveTo(x, yOf(db)) else line.lineTo(x, yOf(db))
        }
        drawPath(line, Palette.Accent, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun StereoizerControls(st: StereoizerSettings, on: Boolean, set: FieldSetter<StereoizerSettings>) {
    val defaults = StereoizerSettings()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Knob("Strength", st.strength, FxRanges.UNIT, defaults.strength, { v -> set("strength") { it.copy(strength = v) } }, ::formatAmount, enabled = on)
        Knob("Timbre", st.timbre, FxRanges.UNIT, defaults.timbre, { v -> set("timbre") { it.copy(timbre = v) } }, ::formatAmount, enabled = on)
        Knob("Focus", st.focus, FxRanges.UNIT, defaults.focus, { v -> set("focus") { it.copy(focus = v) } }, ::formatPercent, enabled = on)
        Knob("L/R delay", st.lrDelay, FxRanges.BIPOLAR, 0f, { v -> set("lrDelay") { it.copy(lrDelay = v) } }, ::formatHaas, bipolar = true, enabled = on)
        Knob("Pan", st.pan, FxRanges.BIPOLAR, 0f, { v -> set("pan") { it.copy(pan = v) } }, ::formatPan, bipolar = true, enabled = on)
    }
}

@Composable
private fun ReverbControls(rev: ReverbSettings, on: Boolean, set: FieldSetter<ReverbSettings>) {
    var tab by remember { mutableStateOf(ReverbTab.MAIN) }
    val defaults = ReverbSettings()
    SegmentedControl(ReverbTab.entries, tab, { it.label }, { tab = it }, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        when (tab) {
            ReverbTab.MAIN -> {
                Knob("Decay", rev.decay, FxRanges.REVERB_DECAY, defaults.decay, { v -> set("decay") { it.copy(decay = v) } }, { "%.1f s".format(it) }, scale = KnobScale.LOG, enabled = on)
                Knob("High damp", rev.damping, FxRanges.UNIT, defaults.damping, { v -> set("damping") { it.copy(damping = v) } }, ::formatPercent, enabled = on)
                Knob("Mix", rev.mix, FxRanges.UNIT, defaults.mix, { v -> set("mix") { it.copy(mix = v) } }, ::formatPercent, enabled = on)
                Knob("Early refl.", rev.earlyReflections, FxRanges.UNIT, defaults.earlyReflections, { v -> set("early") { it.copy(earlyReflections = v) } }, ::formatPercent, enabled = on)
                Knob("Room size", rev.roomSize, FxRanges.UNIT, defaults.roomSize, { v -> set("room") { it.copy(roomSize = v) } }, ::formatPercent, enabled = on)
            }
            ReverbTab.TUNE -> {
                Knob("Pre-delay", rev.preDelay, FxRanges.REVERB_PRE_DELAY, defaults.preDelay, { v -> set("pre") { it.copy(preDelay = v) } }, { "${(it * 1000).roundToInt()} ms" }, enabled = on)
                Knob("Low cut", rev.lowCut, FxRanges.REVERB_LOW_CUT, defaults.lowCut, { v -> set("lowCut") { it.copy(lowCut = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = on)
                Knob("High cut", rev.tone, FxRanges.REVERB_TONE, defaults.tone, { v -> set("tone") { it.copy(tone = v) } }, ::formatHz, scale = KnobScale.LOG, enabled = on)
                Knob("Diffusion", rev.diffusion, FxRanges.UNIT, defaults.diffusion, { v -> set("diffusion") { it.copy(diffusion = v) } }, ::formatPercent, enabled = on)
            }
            ReverbTab.MIXER -> {
                Knob("Dry", rev.dryDb, FxRanges.REVERB_TRIM_DB, 0f, { v -> set("dry") { it.copy(dryDb = v) } }, ::formatGain, enabled = on)
                Knob("Wet", rev.wetDb, FxRanges.REVERB_TRIM_DB, 0f, { v -> set("wet") { it.copy(wetDb = v) } }, ::formatGain, enabled = on)
                Knob("Width", rev.width, FxRanges.UNIT, defaults.width, { v -> set("width") { it.copy(width = v) } }, ::formatPercent, enabled = on)
            }
        }
    }
}

@Composable
private fun GraphicEqControls(geq: GraphicEqSettings, on: Boolean, set: FieldSetter<GraphicEqSettings>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GraphicEqSettings.FREQUENCIES.forEachIndexed { band, hz ->
            val gain = geq.gains.getOrElse(band) { 0f }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatHz(hz), style = TextStyles.Caption)
                VerticalSlider(
                    value = gain,
                    range = FxRanges.GRAPHIC_GAIN,
                    onChange = { v -> set("band$band") { it.copy(gains = it.gains.withBand(band, v)) } },
                    modifier = Modifier.width(GRAPHIC_SLIDER_WIDTH).height(GRAPHIC_SLIDER_HEIGHT),
                    origin = 0f,
                    enabled = on,
                )
                Text("%+.0f".format(gain), style = TextStyles.LcdSmall)
            }
        }
    }
}

@Composable
private fun LevellerControls(lv: LevellerSettings, on: Boolean, level: () -> Float, set: FieldSetter<LevellerSettings>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HorizontalMeter(level)
            Box(Modifier.fillMaxWidth().height(12.dp)) {
                METER_LABELS.forEach { (db, label) ->
                    Text(label, style = TextStyles.LcdSmall, modifier = Modifier.align(BiasAlignment(meterRatio(db) * 2 - 1, 0f)))
                }
            }
            ValueSlider("Gain", lv.gainDb, FxRanges.LEVELLER_GAIN, { v -> set("gain") { it.copy(gainDb = v) } }, ::formatLevel, origin = 0f, enabled = on)
        }
        Knob("Pan", lv.pan, FxRanges.BIPOLAR, 0f, { v -> set("pan") { it.copy(pan = v) } }, ::formatPan, bipolar = true, enabled = on)
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

private fun List<Float>.withBand(band: Int, value: Float): List<Float> =
    List(GraphicEqSettings.BANDS) { index -> if (index == band) value else getOrElse(index) { 0f } }

fun formatHz(hz: Float): String = if (hz >= 1000f) String.format(Locale.ROOT, "%.1fk", hz / 1000f).replace(".0k", "k") else "${hz.toInt()}"

private fun formatGain(db: Float): String = "%+.1f".format(db)

private fun formatAmount(value: Float): String = "${(value * 100).roundToInt()}"

private fun formatPercent(value: Float): String = "${(value * 100).roundToInt()}%"

private fun formatLevel(db: Float): String = "%+.1f dB".format(db)

private fun formatDecibels(db: Float): String = "%.1f dB".format(db)

private fun formatRatio(ratio: Float): String = if (ratio >= RATIO_WHOLE_FROM) "${ratio.roundToInt()}:1" else "%.1f:1".format(ratio)

private fun formatMs(ms: Float): String = if (ms >= MS_WHOLE_FROM) "${ms.roundToInt()} ms" else "%.1f ms".format(ms)

// Positive delays right channel
private fun formatHaas(value: Float): String {
    val ms = abs(value) * Stereoizer.MAX_HAAS_SECONDS * 1000
    return when {
        ms < 0.05 -> "0 ms"
        value > 0 -> "R %.1f".format(ms)
        else -> "L %.1f".format(ms)
    }
}
