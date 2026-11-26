package id.nextfl.app.ui.deck

import id.nextfl.app.editor.EditorViewModel
import id.nextfl.core.edit.ClipSpan
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.model.Clip
import id.nextfl.core.model.EqSettings
import id.nextfl.core.model.Track
import id.nextfl.core.state.EditorState
import kotlin.math.roundToInt

/** One deck's clip and state. */
class DeckInfo(
    val number: Int,
    val track: Track?,
    val clip: Clip?,
    val span: ClipSpan?,
    val assetBpm: Double?,
    val playhead: Double,
) {
    val rate: Double get() = span?.rate ?: 1.0

    val bpm: Double? get() = assetBpm?.let { it * rate }

    val elapsed: Double get() = span?.let { (playhead - it.start).coerceIn(0.0, it.duration) } ?: 0.0

    val remaining: Double get() = span?.let { (it.end - playhead).coerceIn(0.0, it.duration) } ?: 0.0

    val progress: Float get() = span?.let { if (it.duration > 0) (elapsed / it.duration).toFloat() else 0f } ?: 0f

    // Playhead inside this clip
    val isLive: Boolean get() = span?.let { playhead >= it.start && playhead < it.end } ?: false
}

/** Deck N plays track N. */
fun deckInfo(state: EditorState, number: Int, playhead: Double): DeckInfo {
    val doc = state.doc
    val track = doc.tracks.getOrNull(number - 1)
    val spans = track?.let { t -> doc.clips.filter { it.trackId == t.id }.map { it to clipSpan(it, state.assets, doc.bpm) } }.orEmpty()
    val pick = spans.firstOrNull { (_, s) -> playhead >= s.start && playhead < s.end }
        ?: spans.filter { (_, s) -> s.start >= playhead }.minByOrNull { (_, s) -> s.start }
        ?: spans.maxByOrNull { (_, s) -> s.end }
    val clip = pick?.first
    return DeckInfo(number, track, clip, pick?.second, clip?.let { state.assets[it.assetId]?.bpm }, playhead)
}

/** Deck and mixer actions. */
object DeckActions {
    // Tempo slider span, like CDJ
    const val TEMPO_RANGE = 0.16

    // Slider top is minus
    fun tempoPosition(rate: Double): Float = (0.5 - (rate - 1) / (TEMPO_RANGE * 2)).toFloat().coerceIn(0f, 1f)

    fun rateAt(position: Float): Double = 1 + (0.5 - position) * TEMPO_RANGE * 2

    fun setRate(vm: EditorViewModel, clip: Clip, rate: Double) = vm.setDeckRate(clip.id, rate)

    // Reset is one undo step
    fun resetRate(vm: EditorViewModel, clip: Clip) {
        vm.beginGesture()
        vm.setDeckRate(clip.id, 1.0)
        vm.endGesture()
    }

    fun setKey(vm: EditorViewModel, clip: Clip, semitones: Float) =
        vm.updateClip(clip.id, "pitch:${clip.id}") { it.copy(pitchSemitones = semitones.roundToInt().toDouble()) }

    fun toggleKeyLock(vm: EditorViewModel, clip: Clip) = vm.updateClip(clip.id) { it.copy(keepPitch = !it.keepPitch) }

    fun toggleSync(vm: EditorViewModel, clip: Clip) = vm.updateClip(clip.id) { it.copy(syncTempo = !it.syncTempo) }

    fun setTrim(vm: EditorViewModel, clip: Clip, db: Float) = vm.updateClip(clip.id, "gain:${clip.id}") { it.copy(gainDb = db) }

    // CDJ cue: pause, rewind
    fun cue(vm: EditorViewModel, info: DeckInfo, playing: Boolean) {
        val span = info.span ?: return
        if (playing) vm.togglePlay()
        vm.seek(span.start)
    }

    fun setEq(vm: EditorViewModel, track: Track, band: EqBand, db: Float) = vm.updateTrack(track.id, "eq-${band.name}:${track.id}") {
        // Off EQ restarts neutral
        val eq = if (it.eq.enabled) it.eq else EqSettings(enabled = true)
        it.copy(
            eq = when (band) {
                EqBand.HI -> eq.copy(highGain = db)
                EqBand.MID -> eq.copy(midGain = db)
                EqBand.LOW -> eq.copy(lowGain = db)
            },
        )
    }

    fun setVolume(vm: EditorViewModel, track: Track, db: Float) = vm.updateTrack(track.id, "vol:${track.id}") { it.copy(volumeDb = db) }
}

enum class EqBand { HI, MID, LOW }

fun Track.eqGain(band: EqBand): Float = if (!eq.enabled) 0f else when (band) {
    EqBand.HI -> eq.highGain
    EqBand.MID -> eq.midGain
    EqBand.LOW -> eq.lowGain
}

/** Gate pads: beat length, label. */
val GATE_PADS: List<Pair<Double, String>> = listOf(
    1.0 to "1/1", 0.5 to "1/2", 0.25 to "1/4", 0.125 to "1/8",
    0.0625 to "1/16", 0.03125 to "1/32", 0.375 to "3/8", 0.1875 to "3/16",
)
