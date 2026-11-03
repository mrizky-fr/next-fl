package id.nextfl.core.engine

import id.nextfl.core.dsp.Equalizer
import id.nextfl.core.dsp.Reverb
import kotlin.math.abs
import kotlin.math.max

/** Track DSP state across swaps. */
class TrackProcessor(sampleRate: Int, maxBlock: Int) {
    val left = FloatArray(maxBlock)
    val right = FloatArray(maxBlock)
    private val equalizer = Equalizer(sampleRate)
    private val reverb = Reverb(sampleRate)
    private var gainL = 0f
    private var gainR = 0f

    @Volatile
    var peak = 0f
        private set

    fun clear(frames: Int) {
        left.fill(0f, 0, frames)
        right.fill(0f, 0, frames)
    }

    fun process(params: TrackParams, frames: Int) {
        equalizer.configure(params.eq)
        equalizer.process(left, right, frames)
        reverb.configure(params.reverb)
        reverb.process(left, right, frames)
        applyGain(params, frames)
    }

    // Linear ramp avoids zipper noise
    private fun applyGain(params: TrackParams, frames: Int) {
        val targetL = params.gain * if (params.pan > 0) 1 - params.pan else 1f
        val targetR = params.gain * if (params.pan < 0) 1 + params.pan else 1f
        val stepL = (targetL - gainL) / frames
        val stepR = (targetR - gainR) / frames
        var blockPeak = 0f
        for (i in 0 until frames) {
            gainL += stepL
            gainR += stepR
            left[i] *= gainL
            right[i] *= gainR
            blockPeak = max(blockPeak, max(abs(left[i]), abs(right[i])))
        }
        gainL = targetL
        gainR = targetR
        peak = blockPeak
    }
}
