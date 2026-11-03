package id.nextfl.core

import id.nextfl.core.pcm.ArrayPcmSource
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object TestSignals {
    fun sine(frequency: Double, seconds: Double, sampleRate: Int, amplitude: Float = 0.5f): FloatArray =
        FloatArray((seconds * sampleRate).toInt()) { (amplitude * sin(2 * PI * frequency * it / sampleRate)).toFloat() }

    fun stereo(data: FloatArray, sampleRate: Int) = ArrayPcmSource(data, data.copyOf(), sampleRate)

    fun zeroCrossingRate(data: FloatArray, sampleRate: Int): Double {
        var crossings = 0
        for (i in 1 until data.size) if ((data[i - 1] < 0) != (data[i] < 0)) crossings++
        return crossings / (data.size.toDouble() / sampleRate) / 2
    }

    fun drumLoop(bpm: Double, seconds: Double, sampleRate: Int): FloatArray {
        val data = FloatArray((seconds * sampleRate).toInt())
        val beat = 60.0 / bpm * sampleRate
        var seed = 1L
        fun noise(): Double {
            seed = seed * 16807 % 2147483647
            return seed / 2147483647.0 * 2 - 1
        }
        var start = 0.0
        while (start < data.size) {
            burst(data, start.toInt(), sampleRate) { t -> sin(2 * PI * 55 * t) * exp(-t * 25) }
            burst(data, (start + beat / 2).toInt(), sampleRate) { t -> 0.3 * noise() * exp(-t * 80) }
            start += beat
        }
        return data
    }

    private fun burst(data: FloatArray, at: Int, sampleRate: Int, shape: (Double) -> Double) {
        val length = (sampleRate * 0.2).toInt()
        for (i in 0 until length) if (at + i < data.size) data[at + i] += shape(i.toDouble() / sampleRate).toFloat()
    }
}
