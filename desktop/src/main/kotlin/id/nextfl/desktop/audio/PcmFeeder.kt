package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioDecodeException
import id.nextfl.core.pcm.StereoSink
import id.nextfl.core.pcm.StreamingResampler

/** Decoded frames into engine PCM. */
class PcmFeeder(private val output: StereoSink, private val targetRate: Int) {
    private var sink: StereoSink = output
    private var resampler: StreamingResampler? = null
    private var rate = 0
    private var channels = 0
    private var sourceFrames = 0L
    var left = FloatArray(INITIAL_FRAMES)
        private set
    var right = FloatArray(INITIAL_FRAMES)
        private set

    // Rate change restarts resampler
    fun configure(sampleRate: Int, channelCount: Int) {
        if (sampleRate == rate && channelCount == channels) return
        if (sampleRate <= 0) throw AudioDecodeException("Format tidak dikenali")
        resampler?.finish()
        rate = sampleRate
        channels = channelCount.coerceAtLeast(1)
        resampler = if (sampleRate != targetRate) StreamingResampler(sampleRate, targetRate, output) else null
        sink = resampler ?: output
    }

    fun ensure(frames: Int) {
        if (left.size >= frames) return
        left = FloatArray(frames)
        right = FloatArray(frames)
    }

    /** Sends frames already in left/right. */
    fun push(frames: Int) {
        sourceFrames += frames
        if (sourceFrames > rate.toLong() * MAX_SECONDS) throw AudioDecodeException("Durasi maksimal 30 menit")
        sink.write(left, right, frames)
    }

    fun shorts(samples: ShortArray, frames: Int) {
        ensure(frames)
        for (i in 0 until frames) {
            val base = i * channels
            left[i] = samples[base] / SHORT_SCALE
            right[i] = if (channels > 1) samples[base + 1] / SHORT_SCALE else left[i]
        }
        push(frames)
    }

    fun finish() {
        resampler?.finish()
    }

    private companion object {
        const val INITIAL_FRAMES = 4096
        const val SHORT_SCALE = 32768f
        const val MAX_SECONDS = 30L * 60
    }
}
