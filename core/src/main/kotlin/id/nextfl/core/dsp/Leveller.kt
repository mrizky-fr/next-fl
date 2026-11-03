package id.nextfl.core.dsp

import id.nextfl.core.model.LevellerSettings
import id.nextfl.core.util.AudioMath

/** Gain and balance stage. */
class Leveller {
    private var gainL = 1f
    private var gainR = 1f

    // Ramped per block, no zipper
    fun process(settings: LevellerSettings, left: FloatArray, right: FloatArray, count: Int) {
        val gain = if (settings.enabled) AudioMath.dbToGain(settings.gainDb) else 1f
        val pan = if (settings.enabled) settings.pan else 0f
        val targetL = gain * if (pan > 0) 1 - pan else 1f
        val targetR = gain * if (pan < 0) 1 + pan else 1f
        if (targetL == 1f && targetR == 1f && gainL == 1f && gainR == 1f) return
        val stepL = (targetL - gainL) / count
        val stepR = (targetR - gainR) / count
        for (i in 0 until count) {
            gainL += stepL
            gainR += stepR
            left[i] *= gainL
            right[i] *= gainR
        }
        gainL = targetL
        gainR = targetR
    }
}
