package id.nextfl.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import id.nextfl.core.engine.MixEngine

/** One play run, own thread. */
class PlaybackSession(
    private val engine: MixEngine,
    private val tuning: OutputTuning,
    private val onFailure: (AudioStream, Exception) -> Unit,
) : Thread("nextfl-audio"), AudioStream {
    private val track: AudioTrack = buildTrack(engine.sampleRate)
    private val timestamp = AudioTimestamp()
    private val capacityFrames = track.bufferCapacityInFrames.coerceAtLeast(BLOCK_FRAMES)
    private var appliedFrames = 0
    private var lastUnderruns = 0
    private var blocks = 0L
    private var pipelineFrames = 0f

    @Volatile
    private var active = true

    @Volatile
    private var released = false

    @Volatile
    private var framesWritten = 0L

    init {
        if (tuning.baseFrames == 0) tuning.baseFrames = startFrames(engine.sampleRate)
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(BLOCK_FRAMES * 2)
        try {
            tuneBuffer()
            track.play()
            while (active) {
                engine.renderInterleaved(buffer, BLOCK_FRAMES)
                // Dead route, e.g. Bluetooth
                if (!writeFully(buffer)) throw IllegalStateException("AudioTrack write failed")
                framesWritten += BLOCK_FRAMES
                blocks++
                tuneBuffer()
            }
        } catch (error: Exception) {
            if (active) onFailure(this, error)
        } finally {
            released = true
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }

    private fun writeFully(buffer: FloatArray): Boolean {
        var offset = 0
        while (offset < buffer.size && active) {
            val written = track.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) return false
            offset += written
        }
        return true
    }

    // Underrun grows buffer one block
    private fun tuneBuffer() {
        val underruns = track.underrunCount
        if (blocks < WARMUP_BLOCKS) {
            lastUnderruns = underruns
        } else if (underruns > lastUnderruns) {
            lastUnderruns = underruns
            tuning.baseFrames = (tuning.baseFrames + BLOCK_FRAMES).coerceAtMost(capacityFrames)
        }
        if (blocks % PIPELINE_EVERY == 0L) measurePipeline()
        // Device latency already gives lookahead
        val ahead = (tuning.aheadFrames(System.nanoTime()) - pipelineFrames.toInt()).coerceAtLeast(0)
        val wanted = maxOf(tuning.baseFrames, ahead).coerceIn(BLOCK_FRAMES, capacityFrames)
        if (wanted != appliedFrames) {
            track.setBufferSizeInFrames(wanted)
            appliedFrames = wanted
        }
    }

    // Frames queued beyond our buffer
    private fun measurePipeline() {
        if (appliedFrames == 0) return
        val beyond = (framesWritten - audibleOutputFrame() - appliedFrames).coerceAtLeast(0L).toFloat()
        pipelineFrames += (beyond - pipelineFrames) * PIPELINE_SMOOTHING
    }

    override fun begin() = start()

    override fun audibleOutputFrame(): Long {
        if (released) return framesWritten
        val estimate = runCatching {
            if (track.getTimestamp(timestamp)) {
                val elapsed = System.nanoTime() - timestamp.nanoTime
                timestamp.framePosition + elapsed * engine.sampleRate / 1_000_000_000L
            } else {
                track.playbackHeadPosition.toLong()
            }
        }.getOrDefault(0L)
        return estimate.coerceIn(0L, framesWritten)
    }

    // Pause interrupts blocking write
    override fun finish() {
        active = false
        if (!released) runCatching { track.pause() }
        join(JOIN_TIMEOUT_MS)
    }

    private companion object {
        const val BLOCK_FRAMES = AudioStream.BLOCK_FRAMES
        private const val START_BLOCKS = 2
        private const val CAPACITY_BLOCKS = 32
        private const val WARMUP_BLOCKS = 16L
        private const val PIPELINE_EVERY = 8L
        private const val PIPELINE_SMOOTHING = 0.2f
        private const val JOIN_TIMEOUT_MS = 500L
        private const val BYTES_PER_FRAME = 8

        private fun minFrames(sampleRate: Int): Int =
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT) / BYTES_PER_FRAME

        private fun startFrames(sampleRate: Int): Int = maxOf(minFrames(sampleRate), START_BLOCKS * BLOCK_FRAMES)

        // Capacity covers visual lookahead
        private fun buildTrack(sampleRate: Int): AudioTrack {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val capacityFrames = maxOf(minFrames(sampleRate) * 2, CAPACITY_BLOCKS * BLOCK_FRAMES)
            val track = AudioTrack.Builder()
                .setAudioAttributes(PlaybackAttributes.music)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(capacityFrames * BYTES_PER_FRAME)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("Audio output unavailable")
            }
            return track
        }
    }
}

object PlaybackAttributes {
    val music: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}
