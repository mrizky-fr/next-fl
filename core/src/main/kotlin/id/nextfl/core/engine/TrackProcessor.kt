package id.nextfl.core.engine

import id.nextfl.core.model.FxChain
import kotlin.math.abs
import kotlin.math.max

/** Track DSP state across swaps. */
class TrackProcessor(private val sampleRate: Int, maxBlock: Int) {
    val left = FloatArray(maxBlock)
    val right = FloatArray(maxBlock)
    private var chain = FxChainProcessor(sampleRate)
    private var gainL = 0f
    private var gainR = 0f

    @Volatile
    var peak = 0f
        private set
    val history = PeakHistory()

    fun clear(frames: Int) {
        left.fill(0f, 0, frames)
        right.fill(0f, 0, frames)
    }

    fun process(params: TrackParams, frames: Int) = process(params.fx, params.gain, params.pan, frames)

    fun process(fx: FxChain, gain: Float, pan: Float, frames: Int) {
        chain.process(fx, left, right, frames)
        val blockPeak = applyGain(gain, pan, frames)
        peak = if (blockPeak.isFinite()) blockPeak else recover(frames)
    }

    // NaN poisoned FX: start fresh
    private fun recover(frames: Int): Float {
        clear(frames)
        chain = FxChainProcessor(sampleRate)
        return 0f
    }

    // Linear ramp avoids zipper noise
    private fun applyGain(gain: Float, pan: Float, frames: Int): Float {
        val targetL = gain * if (pan > 0) 1 - pan else 1f
        val targetR = gain * if (pan < 0) 1 + pan else 1f
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
        return blockPeak
    }
}
