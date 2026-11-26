package id.nextfl.core.engine

import id.nextfl.core.dsp.BeatGate
import id.nextfl.core.dsp.DjEffects
import id.nextfl.core.dsp.Limiter
import id.nextfl.core.edit.TrackOps
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.pcm.PcmSource
import id.nextfl.core.util.AudioMath
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Render from one thread only
class MixEngine(val sampleRate: Int, private val maxBlock: Int = MAX_BLOCK) {
    @Volatile
    private var graph = RenderGraph.EMPTY

    @Volatile
    private var pendingSeek = -1L

    @Volatile
    var songFrame = 0L
        private set

    private var outputFrame = 0L
    private val processors = ConcurrentHashMap<String, TrackProcessor>()
    private val trackSlots = ArrayList<TrackProcessor>()
    private var voiceTargets = arrayOfNulls<TrackProcessor>(0)
    private var voiceStates = arrayOfNulls<GrainReader.State>(0)
    private var voiceOffsets = DoubleArray(0)
    private var syncedGraph: RenderGraph? = null
    private val masterL = FloatArray(maxBlock)
    private val masterR = FloatArray(maxBlock)
    private val limiter = Limiter(sampleRate)
    private var masterChain = FxChainProcessor(sampleRate)
    private var djEffects = DjEffects(sampleRate)
    private val gate = BeatGate(sampleRate)
    private val masterHistory = PeakHistory()
    private val grains = GrainReader(sampleRate)
    private val grainStates = HashMap<String, GrainReader.State>()
    private var masterGain = 1f
    val tap = MasterTap()

    @Volatile
    private var segments = longArrayOf(0L, 0L)

    @Volatile
    var masterPeak = 0f
        private set

    /** Live playback wraps at song end. */
    @Volatile
    var looping = false

    /** Held gate length in beats. */
    @Volatile
    var gateBeats = BeatGate.OFF

    /** Active deck tempo; zero uses the project tempo. */
    @Volatile
    var gateBpm = 0.0

    /** Latest editor controls while an immutable render graph is being rebuilt. */
    @Volatile
    var liveDocProvider: (() -> ProjectDoc)? = null

    val currentGraph: RenderGraph get() = graph

    fun setGraph(next: RenderGraph) {
        graph = next
    }

    fun seek(frame: Long) {
        pendingSeek = max(0L, frame)
    }

    /** Only while not rendering. */
    fun resetOutputClock(startSongFrame: Long) {
        pendingSeek = -1
        songFrame = max(0L, startSongFrame)
        outputFrame = 0
        segments = longArrayOf(0L, songFrame)
        voiceOffsets.fill(0.0)
        tap.reset()
        processors.values.forEach { it.history.reset() }
        masterHistory.reset()
    }

    fun songFrameAtOutput(output: Long): Long {
        val pairs = segments
        var index = pairs.size - 2
        while (index > 0 && pairs[index] > output) index -= 2
        return pairs[index + 1] + max(0L, output - pairs[index])
    }

    fun trackPeak(trackId: String): Float = processors[trackId]?.peak ?: 0f

    /** Track peak heard at output frame. */
    fun trackPeakAt(trackId: String, output: Long): Float = processors[trackId]?.history?.at(output) ?: 0f

    fun masterPeakAt(output: Long): Float = masterHistory.at(output)

    fun renderInterleaved(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val count = nextChunk(frames - done)
            renderStereo(masterL, masterR, count, copy = false)
            for (i in 0 until count) {
                out[2 * (done + i)] = masterL[i]
                out[2 * (done + i) + 1] = masterR[i]
            }
            done += count
        }
    }

    // Splits block exactly at loop point
    private fun nextChunk(remaining: Int): Int {
        applySeek()
        val end = graph.endFrame
        if (!looping || end <= 0) return remaining
        if (songFrame >= end) jumpTo(0)
        return min(remaining.toLong(), end - songFrame).toInt()
    }

    fun renderStereo(left: FloatArray, right: FloatArray, frames: Int, copy: Boolean = true) {
        require(frames <= maxBlock) { "Block too large" }
        applySeek()
        val current = graph
        if (syncedGraph !== current) syncGraph(current)
        val liveDoc = liveDocProvider?.invoke()?.takeUnless { it === current.sourceDoc }
        for (index in 0 until trackSlots.size) trackSlots[index].clear(frames)
        mixVoices(current, liveDoc, frames)
        mixTracks(current, liveDoc, frames)
        if (copy) {
            masterL.copyInto(left, 0, 0, frames)
            masterR.copyInto(right, 0, 0, frames)
        }
        tap.write(masterL, masterR, frames, outputFrame)
        for (index in 0 until trackSlots.size) {
            val processor = trackSlots[index]
            processor.history.write(outputFrame, frames, processor.peak)
        }
        masterHistory.write(outputFrame, frames, masterPeak)
        songFrame += frames
        outputFrame += frames
    }

    private fun applySeek() {
        val seek = pendingSeek
        if (seek < 0) return
        pendingSeek = -1
        jumpTo(seek)
    }

    private fun jumpTo(frame: Long) {
        songFrame = frame
        voiceOffsets.fill(0.0)
        segments = (segments + longArrayOf(outputFrame, frame)).takeLast(MAX_SEGMENTS * 2).toLongArray()
    }

    // Allocates only on graph swap
    private fun syncGraph(current: RenderGraph) {
        val previousVoices = syncedGraph?.voices.orEmpty()
        val previousOffsets = voiceOffsets
        val previousById = previousVoices.indices.associateBy { previousVoices[it].id }
        val ids = current.tracks.mapTo(HashSet()) { it.id }
        processors.keys.retainAll(ids)
        trackSlots.clear()
        for (params in current.tracks) trackSlots.add(processors.getOrPut(params.id) { TrackProcessor(sampleRate, maxBlock) })
        val voices = current.voices
        voiceOffsets = DoubleArray(voices.size) { index ->
            val voice = voices[index]
            val oldIndex = previousById[voice.id] ?: return@DoubleArray 0.0
            val old = previousVoices[oldIndex]
            if (old.source !== voice.source || old.sourceStart != voice.sourceStart) return@DoubleArray 0.0
            val oldOffset = previousOffsets[oldIndex]
            if (old.step == voice.step && old.startFrame == voice.startFrame) return@DoubleArray oldOffset
            if (old.step == voice.step || songFrame !in old.startFrame until old.endFrame || songFrame !in voice.startFrame until voice.endFrame) return@DoubleArray 0.0
            // Keep the current source sample when a live tempo change rebuilds the graph.
            oldOffset + (songFrame - old.startFrame) * old.step - (songFrame - voice.startFrame) * voice.step
        }
        grainStates.keys.retainAll(voices.mapTo(HashSet()) { it.id })
        voiceTargets = Array(voices.size) { processors[voices[it].trackId] }
        voiceStates = Array(voices.size) { grainState(voices[it]) }
        syncedGraph = current
    }

    private fun grainState(voice: ClipVoice): GrainReader.State? =
        if (voice.granular) grainStates.getOrPut(voice.id) { GrainReader.State() } else null

    private fun mixVoices(current: RenderGraph, liveDoc: ProjectDoc?, frames: Int) {
        val blockStart = songFrame
        val blockEnd = blockStart + frames
        val voices = current.voices
        for (index in 0 until voices.size) {
            val voice = voices[index]
            if (voice.endFrame <= blockStart || voice.startFrame >= blockEnd) continue
            val clipGain = if (liveDoc == null) voice.clipGain else
                liveDoc.clips.firstOrNull { it.id == voice.id }?.let { AudioMath.dbToGain(it.gainDb) } ?: 0f
            if (clipGain == 0f) continue
            val target = voiceTargets[index] ?: continue
            val state = voiceStates[index]
            val from = max(0L, voice.startFrame - blockStart).toInt()
            val to = min(frames.toLong(), voice.endFrame - blockStart).toInt()
            for (i in from until to) {
                val local = blockStart + i - voice.startFrame
                val gain = voice.envelope.valueAt(local.toDouble() / sampleRate).toFloat() * clipGain
                if (gain == 0f) continue
                if (state != null) {
                    grains.sample(voice, state, local, voiceOffsets[index])
                    target.left[i] += grains.left * gain
                    target.right[i] += grains.right * gain
                    continue
                }
                val position = voice.sourceStart + local * voice.step + voiceOffsets[index]
                target.left[i] += Interpolator.left(voice.source, position) * gain
                target.right[i] += Interpolator.right(voice.source, position) * gain
            }
        }
    }

    private fun mixTracks(current: RenderGraph, liveDoc: ProjectDoc?, frames: Int) {
        masterL.fill(0f, 0, frames)
        masterR.fill(0f, 0, frames)
        val tracks = current.tracks
        for (index in 0 until tracks.size) {
            val processor = trackSlots[index]
            val params = tracks[index]
            val liveTrack = liveDoc?.tracks?.firstOrNull { it.id == params.id }
            val gain = if (liveDoc == null) params.gain else if (liveTrack != null && TrackOps.isAudible(liveTrack, liveDoc.tracks))
                AudioMath.dbToGain(liveTrack.volumeDb) else 0f
            processor.process(liveTrack?.fx ?: params.fx, gain, liveTrack?.pan ?: params.pan, frames)
            for (i in 0 until frames) {
                masterL[i] += processor.left[i]
                masterR[i] += processor.right[i]
            }
        }
        masterChain.process(liveDoc?.masterFx ?: current.masterFx, masterL, masterR, frames)
        djEffects.process(liveDoc?.dj ?: current.dj, liveDoc?.bpm ?: current.bpm, masterL, masterR, frames)
        gate.process(gateBeats, gateBpm.takeIf { it > 0.0 } ?: liveDoc?.bpm ?: current.bpm, songFrame, masterL, masterR, frames)
        if (!masterIsFinite(frames)) recoverMaster(frames)
        val targetGain = liveDoc?.let { AudioMath.dbToGain(it.masterDb) } ?: current.masterGain
        val step = (targetGain - masterGain) / frames
        for (i in 0 until frames) {
            masterGain += step
            masterL[i] *= masterGain
            masterR[i] *= masterGain
        }
        masterGain = targetGain
        limiter.process(masterL, masterR, frames)
        var peak = 0f
        for (i in 0 until frames) peak = max(peak, max(abs(masterL[i]), abs(masterR[i])))
        masterPeak = peak
    }

    private fun masterIsFinite(frames: Int): Boolean {
        var sum = 0f
        for (i in 0 until frames) sum += abs(masterL[i]) + abs(masterR[i])
        return sum.isFinite()
    }

    // NaN poisoned FX: start fresh
    private fun recoverMaster(frames: Int) {
        masterL.fill(0f, 0, frames)
        masterR.fill(0f, 0, frames)
        masterChain = FxChainProcessor(sampleRate)
        djEffects = DjEffects(sampleRate)
    }

    companion object {
        const val MAX_BLOCK = 2048
        private const val MAX_SEGMENTS = 8
    }
}

/** Catmull-Rom source interpolation. */
internal object Interpolator {
    fun left(source: PcmSource, position: Double): Float = sample(position) { source.left(it) }

    fun right(source: PcmSource, position: Double): Float = sample(position) { source.right(it) }

    private inline fun sample(position: Double, read: (Int) -> Float): Float {
        val index = floor(position).toInt()
        val t = (position - index).toFloat()
        if (t == 0f) return read(index)
        val y0 = read(index - 1)
        val y1 = read(index)
        val y2 = read(index + 1)
        val y3 = read(index + 2)
        val c1 = 0.5f * (y2 - y0)
        val c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
        val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
        return ((c3 * t + c2) * t + c1) * t + y1
    }
}
