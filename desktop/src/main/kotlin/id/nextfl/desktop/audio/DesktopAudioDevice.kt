package id.nextfl.desktop.audio

import id.nextfl.app.audio.AudioDevice
import id.nextfl.app.audio.AudioStream
import id.nextfl.app.audio.OutputTuning
import id.nextfl.core.engine.MixEngine
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Default Windows output via Java Sound. */
class DesktopAudioDevice : AudioDevice {
    // No focus concept on desktop
    override fun acquire(onInterrupted: () -> Unit): Boolean = true

    override fun release() = Unit

    override fun open(engine: MixEngine, tuning: OutputTuning, onFailure: (AudioStream, Exception) -> Unit): AudioStream =
        DesktopAudioStream(engine, tuning, onFailure)
}

/** One play run, own thread. */
private class DesktopAudioStream(
    private val engine: MixEngine,
    private val tuning: OutputTuning,
    private val onFailure: (AudioStream, Exception) -> Unit,
) : Thread("nextfl-audio"), AudioStream {
    private val format = AudioFormat(engine.sampleRate.toFloat(), BITS, CHANNELS, true, false)
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format).apply { open(format, CAPACITY_FRAMES * BYTES_PER_FRAME) }
    private val capacityFrames = line.bufferSize / BYTES_PER_FRAME
    private var blocks = 0L

    @Volatile
    private var active = true

    @Volatile
    private var released = false

    @Volatile
    private var framesWritten = 0L

    init {
        priority = MAX_PRIORITY
        isDaemon = true
        if (tuning.baseFrames == 0) tuning.baseFrames = START_FRAMES
    }

    override fun begin() = start()

    override fun run() {
        val floats = FloatArray(BLOCK_FRAMES * CHANNELS)
        val bytes = ByteArray(BLOCK_FRAMES * BYTES_PER_FRAME)
        try {
            line.start()
            while (active) {
                engine.renderInterleaved(floats, BLOCK_FRAMES)
                toPcm16(floats, bytes)
                waitForRoom()
                if (!active) break
                checkUnderrun()
                line.write(bytes, 0, bytes.size)
                framesWritten += BLOCK_FRAMES
                blocks++
            }
        } catch (error: Exception) {
            if (active) onFailure(this, error)
        } finally {
            released = true
            runCatching { line.stop() }
            runCatching { line.flush() }
            runCatching { line.close() }
        }
    }

    // Keeps queue near wanted size
    private fun waitForRoom() {
        while (active && queuedFrames() + BLOCK_FRAMES > wantedFrames()) sleep(POLL_MS)
    }

    private fun wantedFrames(): Int {
        val ahead = tuning.aheadFrames(System.nanoTime())
        return maxOf(tuning.baseFrames, ahead).coerceIn(BLOCK_FRAMES * 2, capacityFrames)
    }

    // Empty queue means underrun
    private fun checkUnderrun() {
        if (blocks < WARMUP_BLOCKS || queuedFrames() > 0) return
        tuning.baseFrames = (tuning.baseFrames + BLOCK_FRAMES).coerceAtMost(capacityFrames)
    }

    private fun queuedFrames(): Long = framesWritten - line.longFramePosition

    override fun audibleOutputFrame(): Long {
        if (released) return framesWritten
        return runCatching { line.longFramePosition }.getOrDefault(0L).coerceIn(0L, framesWritten)
    }

    // Stop unblocks pending write
    override fun finish() {
        active = false
        if (!released) runCatching { line.stop() }
        join(JOIN_TIMEOUT_MS)
    }

    // NaN becomes silence, no throw
    private fun toPcm16(input: FloatArray, output: ByteArray) {
        for (i in input.indices) {
            val value = (input[i].coerceIn(-1f, 1f) * SHORT_MAX).toInt()
            output[i * 2] = value.toByte()
            output[i * 2 + 1] = (value shr 8).toByte()
        }
    }

    private companion object {
        const val BLOCK_FRAMES = AudioStream.BLOCK_FRAMES
        const val BITS = 16
        const val CHANNELS = 2
        const val BYTES_PER_FRAME = 4
        const val START_FRAMES = BLOCK_FRAMES * 4
        const val CAPACITY_FRAMES = BLOCK_FRAMES * 32
        const val WARMUP_BLOCKS = 16L
        const val POLL_MS = 1L
        const val JOIN_TIMEOUT_MS = 500L
        const val SHORT_MAX = 32767f
    }
}
