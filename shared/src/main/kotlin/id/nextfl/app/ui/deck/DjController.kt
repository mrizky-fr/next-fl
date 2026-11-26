package id.nextfl.app.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.playlist.formatDb
import id.nextfl.app.ui.playlist.formatSemitones
import id.nextfl.app.ui.visual.VisualizerCanvas
import id.nextfl.app.ui.visual.rememberRotationImages
import id.nextfl.core.dsp.BeatGate
import id.nextfl.core.model.FxRanges
import id.nextfl.core.state.EditorState
import id.nextfl.core.util.AudioMath
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SCREEN_RATIO = 16f / 9f
private const val PLAYHEAD_POLL_MS = 100L
private const val OFF = 0.01f
private val TRIM_RANGE = -24f..24f
private val MASTER_RANGE = -24f..6f

/** Sizes derived from available space. */
private class Metrics(width: Dp, height: Dp) {
    val pad = (height * 0.03f).coerceIn(5.dp, 14.dp)
    val gap = (height * 0.018f).coerceIn(3.dp, 10.dp)
    val inner = height - pad * 2
    val compact = inner < 300.dp
    val strip = if (compact) (inner * 0.36f).coerceIn(70.dp, 96.dp) else (inner * 0.14f).coerceIn(46.dp, 74.dp)
    val center = min(width * 0.34f, inner * 0.60f * SCREEN_RATIO)
    val deck = (width - pad * 2 - gap * 4 - strip * 2 - center) / 2
    val header = (inner * 0.10f).coerceIn(22.dp, 40.dp)
    val bottom = (inner * 0.30f).coerceIn(56.dp, 120.dp)
    val side = (deck * 0.17f).coerceIn(44.dp, 78.dp)
    val tempo = (deck * 0.13f).coerceIn(34.dp, 56.dp)
    val corner = (height * 0.03f).coerceIn(6.dp, 16.dp)
}

/** Two-deck 3D controller, screen center. */
@Composable
fun DjController(vm: EditorViewModel, state: EditorState, playing: Boolean, modifier: Modifier = Modifier) {
    val playhead by rememberPlayhead(vm)
    val left = deckInfo(state, 1, playhead)
    val right = deckInfo(state, 2, playhead)
    val held = remember { mutableStateListOf<Double>() }
    DisposableEffect(Unit) { onDispose { vm.setGate(BeatGate.OFF) } }
    BoxWithConstraints(modifier) {
        val m = Metrics(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            val inset = m.pad.toPx() * 0.35f
            drawChassis(Rect(inset, inset, size.width - inset, size.height - inset), m.corner.toPx())
        }
        Row(Modifier.fillMaxSize().padding(m.pad), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            DeckSection(vm, left, playing, held, m, Modifier.width(m.deck).fillMaxHeight())
            ChannelStrip(vm, left, m, Modifier.width(m.strip).fillMaxHeight())
            CenterSection(vm, state, m, Modifier.width(m.center).fillMaxHeight())
            ChannelStrip(vm, right, m, Modifier.width(m.strip).fillMaxHeight())
            DeckSection(vm, right, playing, held, m, Modifier.width(m.deck).fillMaxHeight())
        }
    }
}

// Polled, not per frame
@Composable
private fun rememberPlayhead(vm: EditorViewModel): State<Double> {
    val playhead = remember { mutableDoubleStateOf(vm.position()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = vm.position()
            if (abs(now - playhead.doubleValue) > 0.02) playhead.doubleValue = now
            delay(PLAYHEAD_POLL_MS)
        }
    }
    return playhead
}

private fun Modifier.plate(corner: Dp) = drawBehind { drawPlate(Rect(0f, 0f, size.width, size.height), corner.toPx()) }

@Composable
private fun DeckSection(vm: EditorViewModel, info: DeckInfo, playing: Boolean, held: SnapshotStateList<Double>, m: Metrics, modifier: Modifier) {
    val clip = info.clip
    Column(modifier.plate(m.corner).padding(m.gap), verticalArrangement = Arrangement.spacedBy(m.gap)) {
        DeckHeader(info, Modifier.fillMaxWidth().height(m.header))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            Column(Modifier.width(m.side).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                HardKnob(
                    label = "KEY",
                    value = clip?.pitchSemitones?.toFloat() ?: 0f,
                    range = FxRanges.PITCH,
                    default = 0f,
                    onChange = { v -> clip?.let { DeckActions.setKey(vm, it, v) } },
                    format = ::formatSemitones,
                    size = m.side * 0.72f,
                    bipolar = true,
                    enabled = clip != null,
                    showValue = true,
                )
                val button = Modifier.fillMaxWidth().height((m.side * 0.42f).coerceAtMost(30.dp))
                LedButton("KEY LOCK", clip?.keepPitch == true, { clip?.let { DeckActions.toggleKeyLock(vm, it) } }, button, enabled = clip != null)
                LedButton("SYNC", clip?.syncTempo == true, { clip?.let { DeckActions.toggleSync(vm, it) } }, button, enabled = clip != null && info.assetBpm != null)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                DeckJog(vm, info, playing, Modifier.size(min(maxWidth, maxHeight)))
            }
            TempoColumn(vm, info, m, Modifier.width(m.tempo).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(m.bottom), horizontalArrangement = Arrangement.spacedBy(m.gap), verticalAlignment = Alignment.CenterVertically) {
            val round = min(m.bottom * 0.85f, m.deck * 0.17f).coerceAtMost(88.dp)
            RoundDeckButton("CUE", ArtColors.Cue, !playing && info.isLive, { DeckActions.cue(vm, info, playing) }, Modifier.size(round))
            RoundDeckButton("PLAY", ArtColors.Play, playing, vm::togglePlay, Modifier.size(round), playGlyph = true)
            GatePads(vm, held, m, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun DeckHeader(info: DeckInfo, modifier: Modifier) {
    val clip = info.clip
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .drawBehind {
                drawRect(ArtColors.Glass)
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)))
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val text = (maxHeight.value * 0.42f).coerceIn(8f, 14f).sp
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${info.number}", style = printStyle(text, Color.White).copy(fontWeight = FontWeight.Bold), modifier = Modifier.deckBadge())
            val title = when {
                info.track == null -> "Track ${info.number} belum ada"
                clip == null -> "${info.track.name} kosong"
                else -> clip.name
            }
            Text(title, style = printStyle(text, ArtColors.Print), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (clip != null) {
                Text("−" + clock(info.remaining), style = printStyle(text, ArtColors.Led).copy(fontFeatureSettings = "tnum"), maxLines = 1)
            }
        }
    }
}

private fun Modifier.deckBadge() = drawBehind {
    drawRoundRect(ArtColors.LedDeep, Offset(-size.height * 0.25f, 0f), androidx.compose.ui.geometry.Size(size.width + size.height * 0.5f, size.height), CornerRadius(3f))
}

@Composable
private fun TempoColumn(vm: EditorViewModel, info: DeckInfo, m: Metrics, modifier: Modifier) {
    val clip = info.clip
    val text = (m.tempo.value * 0.2f).coerceIn(7f, 10f).sp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("TEMPO", style = printStyle(text), maxLines = 1)
        if (!m.compact) Text("−", style = printStyle(text), maxLines = 1)
        HardFader(
            position = DeckActions.tempoPosition(info.rate),
            onChange = { p -> clip?.let { DeckActions.setRate(vm, it, DeckActions.rateAt(p)) } },
            onReset = { clip?.let { DeckActions.resetRate(vm, it) } },
            description = "Tempo deck ${info.number}",
            modifier = Modifier.weight(1f).fillMaxWidth(),
            centerDetent = true,
            enabled = clip != null,
            onStart = vm::beginGesture,
            onEnd = vm::endGesture,
        )
        if (!m.compact) Text("+", style = printStyle(text), maxLines = 1)
        Text("±${(DeckActions.TEMPO_RANGE * 100).roundToInt()}%", style = printStyle(text, ArtColors.Print), maxLines = 1)
    }
}

// Last held pad wins
@Composable
private fun GatePads(vm: EditorViewModel, held: SnapshotStateList<Double>, m: Metrics, modifier: Modifier) {
    val rows = if (m.bottom >= 56.dp) 2 else 1
    val perRow = GATE_PADS.size / rows
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("BEAT GATE", style = printStyle((m.bottom.value * 0.11f).coerceIn(7f, 10f).sp), maxLines = 1)
        for (row in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.gap * 0.6f)) {
                GATE_PADS.subList(row * perRow, (row + 1) * perRow).forEach { (beats, label) ->
                    HoldPad(
                        label = label,
                        color = ArtColors.Led,
                        active = held.lastOrNull() == beats,
                        onPress = {
                            held.remove(beats)
                            held.add(beats)
                            vm.setGate(beats)
                        },
                        onRelease = {
                            held.remove(beats)
                            vm.setGate(held.lastOrNull() ?: BeatGate.OFF)
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelStrip(vm: EditorViewModel, info: DeckInfo, m: Metrics, modifier: Modifier) {
    val track = info.track
    val clip = info.clip
    val knob = if (m.compact) m.strip * 0.34f else m.strip * 0.6f
    val showValue = !m.compact
    val trim: @Composable () -> Unit = {
        HardKnob("TRIM", clip?.gainDb ?: 0f, TRIM_RANGE, 0f, { v -> clip?.let { DeckActions.setTrim(vm, it, v) } }, ::formatDb, knob, bipolar = true, enabled = clip != null, showValue = showValue)
    }
    val eq: @Composable (EqBand) -> Unit = { band ->
        HardKnob(
            band.name,
            track?.eqGain(band) ?: 0f,
            FxRanges.EQ_GAIN,
            0f,
            { v -> track?.let { DeckActions.setEq(vm, it, band, v) } },
            ::formatDb,
            knob,
            bipolar = true,
            enabled = track != null,
            showValue = showValue,
        )
    }
    Column(modifier.plate(m.corner).padding(vertical = m.gap), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(m.gap * 0.5f)) {
        if (m.compact) {
            // Short screens: 2x2 knobs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                trim()
                eq(EqBand.HI)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                eq(EqBand.MID)
                eq(EqBand.LOW)
            }
        } else {
            trim()
            EqBand.entries.forEach { eq(it) }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HardFader(
                position = AudioMath.dbToFader(track?.volumeDb ?: 0f),
                onChange = { p -> track?.let { DeckActions.setVolume(vm, it, (AudioMath.faderToDb(p) * 10).roundToInt() / 10f) } },
                onReset = { track?.let { DeckActions.setVolume(vm, it, 0f) } },
                description = "Volume channel ${info.number}",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                enabled = track != null,
                onStart = vm::beginGesture,
                onEnd = vm::endGesture,
            )
            LevelMeter({ track?.let { vm.trackLevel(it.id) } ?: 0f }, Modifier.width(5.dp).fillMaxHeight().padding(vertical = 6.dp))
        }
        if (!m.compact) Text("CH ${info.number}", style = printStyle((m.strip.value * 0.16f).coerceIn(7f, 10f).sp, ArtColors.Print), maxLines = 1)
    }
}

@Composable
private fun CenterSection(vm: EditorViewModel, state: EditorState, m: Metrics, modifier: Modifier) {
    val settings by vm.visualSettings.collectAsState()
    val images = rememberRotationImages(vm)
    val dj = state.doc.dj
    Column(modifier, verticalArrangement = Arrangement.spacedBy(m.gap)) {
        // Live visual, export framing
        Box(Modifier.fillMaxWidth().aspectRatio(SCREEN_RATIO)) {
            Canvas(Modifier.fillMaxSize()) { drawScreenBezel(Rect(0f, 0f, size.width, size.height), m.corner.toPx() * 0.6f) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val inset = min(maxWidth, maxHeight) * 0.035f
                Box(Modifier.fillMaxSize().padding(inset)) {
                    VisualizerCanvas(vm, settings, images)
                    Canvas(Modifier.fillMaxSize()) { drawScreenGlass(Rect(0f, 0f, size.width, size.height)) }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().plate(m.corner).padding(m.gap), contentAlignment = Alignment.Center) {
            val knob = min((maxWidth - 24.dp) / 5.6f, maxHeight * 0.5f).coerceIn(24.dp, 84.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                HardKnob("FILTER", dj.filter, FxRanges.BIPOLAR, 0f, { v -> vm.updateDj("filter") { it.copy(filter = v) } }, ::formatFilter, knob, bipolar = true, showValue = !m.compact)
                HardKnob("ECHO", dj.echo, FxRanges.UNIT, 0f, { v -> vm.updateDj("echo") { it.copy(echo = v) } }, ::formatAmount, knob, showValue = !m.compact)
                HardKnob("FLANGER", dj.flanger, FxRanges.UNIT, 0f, { v -> vm.updateDj("flanger") { it.copy(flanger = v) } }, ::formatAmount, knob, showValue = !m.compact)
                HardKnob("CRUSH", dj.crush, FxRanges.UNIT, 0f, { v -> vm.updateDj("crush") { it.copy(crush = v) } }, ::formatAmount, knob, showValue = !m.compact)
                HardKnob("MASTER", state.doc.masterDb, MASTER_RANGE, 0f, vm::setMasterDb, ::formatDb, knob, bipolar = true, showValue = !m.compact)
                LevelMeter(vm::masterLevel, Modifier.width(6.dp).height(knob * 1.4f))
            }
        }
    }
}

private fun formatFilter(value: Float): String = when {
    value <= -OFF -> "LPF ${(-value * 100).roundToInt()}%"
    value >= OFF -> "HPF ${(value * 100).roundToInt()}%"
    else -> "Off"
}

private fun formatAmount(value: Float): String = if (value < OFF) "Off" else "${(value * 100).roundToInt()}%"

private fun clock(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0)
    val minutes = (total / 60).toInt()
    return String.format(Locale.ROOT, "%d:%04.1f", minutes, total - minutes * 60)
}
