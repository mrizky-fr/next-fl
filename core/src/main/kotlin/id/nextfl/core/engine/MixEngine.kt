package id.nextfl.core.engine

import id.nextfl.core.dsp.DjEffects
import id.nextfl.core.dsp.Limiter
import id.nextfl.core.pcm.PcmSource
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
    private val masterL = FloatArray(maxBlock)
    private val masterR = FloatArray(maxBlock)
    private val limiter = Limiter(sampleRate)
    private val masterChain = FxChainProcessor(sampleRate)
    private val djEffects = DjEffects(sampleRate)
    private val masterHistory = PeakHistory()
    private val grains = GrainReader(sampleRate)
    private val grainStates = HashMap<String, GrainReader.State>()
    private var grainGraph: RenderGraph? = null
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
        syncProcessors(current)
        processors.values.forEach { it.clear(frames) }
        mixVoices(current, frames)
        mixTracks(current, frames)
        if (copy) {
            masterL.copyInto(left, 0, 0, frames)
            masterR.copyInto(right, 0, 0, frames)
        }
        tap.write(masterL, masterR, frames, outputFrame)
        processors.values.forEach { it.history.write(outputFrame, frames, it.peak) }
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
        segments = (segments + longArrayOf(outputFrame, frame)).takeLast(MAX_SEGMENTS * 2).toLongArray()
    }

    private fun syncProcessors(current: RenderGraph) {
        val ids = current.tracks.mapTo(HashSet()) { it.id }
        processors.keys.retainAll(ids)
        for (id in ids) processors.getOrPut(id) { TrackProcessor(sampleRate, maxBlock) }
    }

    private fun mixVoices(current: RenderGraph, frames: Int) {
        if (grainGraph !== current) {
            grainGraph = current
            grainStates.keys.retainAll(current.voices.mapTo(HashSet()) { it.id })
        }
        val blockStart = songFrame
        val blockEnd = blockStart + frames
        for (voice in current.voices) {
            if (voice.endFrame <= blockStart || voice.startFrame >= blockEnd) continue
            val target = processors[voice.trackId] ?: continue
            val from = max(0L, voice.startFrame - blockStart).toInt()
            val to = min(frames.toLong(), voice.endFrame - blockStart).toInt()
            for (i in from until to) {
                val local = blockStart + i - voice.startFrame
                val gain = voice.envelope.valueAt(local.toDouble() / sampleRate).toFloat()
                if (gain == 0f) continue
                if (voice.granular) {
                    grains.sample(voice, grainStates.getOrPut(voice.id) { GrainReader.State() }, local)
                    target.left[i] += grains.left * gain
                    target.right[i] += grains.right * gain
                    continue
                }
                val position = voice.sourceStart + local * voice.step
                target.left[i] += Interpolator.left(voice.source, position) * gain
                target.right[i] += Interpolator.right(voice.source, position) * gain
            }
        }
    }

    private fun mixTracks(current: RenderGraph, frames: Int) {
        masterL.fill(0f, 0, frames)
        masterR.fill(0f, 0, frames)
        for (params in current.tracks) {
            val processor = processors[params.id] ?: continue
            processor.process(params, frames)
            for (i in 0 until frames) {
                masterL[i] += processor.left[i]
                masterR[i] += processor.right[i]
            }
        }
        masterChain.process(current.masterFx, masterL, masterR, frames)
        djEffects.process(current.dj, current.bpm, masterL, masterR, frames)
        val step = (current.masterGain - masterGain) / frames
        var peak = 0f
        for (i in 0 until frames) {
            masterGain += step
            masterL[i] *= masterGain
            masterR[i] *= masterGain
        }
        masterGain = current.masterGain
        limiter.process(masterL, masterR, frames)
        for (i in 0 until frames) peak = max(peak, max(abs(masterL[i]), abs(masterR[i])))
        masterPeak = peak
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
