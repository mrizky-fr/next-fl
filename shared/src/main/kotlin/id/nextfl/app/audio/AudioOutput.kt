package id.nextfl.app.audio

import id.nextfl.core.engine.MixEngine

/** Speaker access per OS. */
interface AudioDevice {
    /** False when output is busy. */
    fun acquire(onInterrupted: () -> Unit): Boolean

    fun release()

    fun open(engine: MixEngine, tuning: OutputTuning, onFailure: (AudioStream, Exception) -> Unit): AudioStream
}

/** One play run, own thread. */
interface AudioStream {
    fun begin()

    /** Frame currently being heard. */
    fun audibleOutputFrame(): Long

    fun finish()

    companion object {
        // Small blocks: edits heard sooner
        const val BLOCK_FRAMES = 512
    }
}

/** Output sizing shared across sessions. */
class OutputTuning {
    // Grows after underruns, persists
    @Volatile
    var baseFrames = 0

    @Volatile
    private var requestedAhead = 0

    @Volatile
    private var requestedAtNanos = 0L

    /** Visuals need audio rendered ahead. */
    fun requestAhead(frames: Int) {
        requestedAhead = frames
        requestedAtNanos = System.nanoTime()
    }

    fun aheadFrames(nowNanos: Long): Int = if (nowNanos - requestedAtNanos > AHEAD_EXPIRY_NANOS) 0 else requestedAhead

    private companion object {
        const val AHEAD_EXPIRY_NANOS = 500_000_000L
    }
}
