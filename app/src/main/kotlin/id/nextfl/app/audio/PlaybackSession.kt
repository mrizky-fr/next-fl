package id.nextfl.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import id.nextfl.core.engine.MixEngine

/** One play run, own thread. */
class PlaybackSession(private val engine: MixEngine) : Thread("nextfl-audio") {
    private val track: AudioTrack = buildTrack(engine.sampleRate)
    private val timestamp = AudioTimestamp()

    @Volatile
    private var active = true

    @Volatile
    private var released = false

    @Volatile
    private var framesWritten = 0L

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(BLOCK_FRAMES * 2)
        try {
            track.play()
            while (active) {
                engine.renderInterleaved(buffer, BLOCK_FRAMES)
                if (!writeFully(buffer)) break
                framesWritten += BLOCK_FRAMES
            }
        } finally {
            released = true
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
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

    /** Frame currently being heard. */
    fun audibleOutputFrame(): Long {
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

    fun finish() {
        active = false
        join(JOIN_TIMEOUT_MS)
    }

    private companion object {
        // Small blocks: edits heard sooner
        const val BLOCK_FRAMES = 512
        const val BUFFER_BLOCKS = 4
        const val JOIN_TIMEOUT_MS = 500L
        const val BYTES_PER_FRAME = 8

        fun buildTrack(sampleRate: Int): AudioTrack {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            return AudioTrack.Builder()
                .setAudioAttributes(PlaybackAttributes.music)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, BLOCK_FRAMES * BYTES_PER_FRAME * BUFFER_BLOCKS))
                .build()
        }
    }
}

object PlaybackAttributes {
    val music: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}
