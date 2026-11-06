package id.nextfl.core.engine

import id.nextfl.core.dsp.Compressor
import id.nextfl.core.dsp.Equalizer
import id.nextfl.core.dsp.GraphicEqualizer
import id.nextfl.core.dsp.Leveller
import id.nextfl.core.dsp.Reverb
import id.nextfl.core.dsp.Stereoizer
import id.nextfl.core.model.FxChain

/** Insert chain for tracks and master. */
class FxChainProcessor(sampleRate: Int) {
    private val equalizer = Equalizer(sampleRate)
    private val compressor = Compressor(sampleRate)
    private val stereoizer = Stereoizer(sampleRate)
    private val reverb = Reverb(sampleRate)
    private val graphicEq = GraphicEqualizer(sampleRate)
    private val leveller = Leveller()

    val gainReductionDb: Float get() = compressor.gainReductionDb

    fun process(chain: FxChain, left: FloatArray, right: FloatArray, frames: Int) {
        equalizer.configure(chain.eq)
        equalizer.process(left, right, frames)
        compressor.configure(chain.compressor)
        compressor.process(left, right, frames)
        stereoizer.configure(chain.stereoizer)
        stereoizer.process(left, right, frames)
        reverb.configure(chain.reverb)
        reverb.process(left, right, frames)
        graphicEq.configure(chain.graphicEq)
        graphicEq.process(left, right, frames)
        leveller.process(chain.leveller, left, right, frames)
    }
}
