package id.nextfl.core.engine

import id.nextfl.core.TestSignals
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Clip
import id.nextfl.core.model.Defaults
import id.nextfl.core.pcm.ArrayStereoSink
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Realtime pitch and tempo, independent. */
class PitchShiftTest {
    private val sampleRate = 44_100
    private val tone = TestSignals.stereo(TestSignals.sine(440.0, 2.0, sampleRate, 0.4f), sampleRate)
    private val assets = mapOf("tone" to AudioAsset("tone", "Tone", 2.0, null))
    private val resolver = SourceResolver { assetId -> if (assetId == "tone") tone else null }

    private class Result(val seconds: Double, val frequency: Double, val samples: FloatArray)

    private fun render(edit: (Clip) -> Clip): Result {
        val base = Defaults.project().copy(bpm = 120.0)
        val track = base.tracks[0]
        val clip = edit(Defaults.clip(track.id, "tone", "Tone", 0.0, 2.0))
        val graph = RenderGraphBuilder.build(base.copy(tracks = listOf(track), clips = listOf(clip)), assets, sampleRate, resolver)
        val sink = ArrayStereoSink()
        OfflineRenderer.render(graph, sampleRate, sink)
        val output = sink.toSource(sampleRate)
        val middle = (0.2 * sampleRate).toInt() until (graph.endFrame - 0.2 * sampleRate).toInt()
        val left = FloatArray(middle.count()) { output.left(middle.first + it) }
        return Result(graph.endFrame.toDouble() / sampleRate, TestSignals.zeroCrossingRate(left, sampleRate), left)
    }

    private fun assertNear(expected: Double, actual: Double, tolerance: Double, label: String) =
        assertTrue(abs(actual - expected) <= expected * tolerance, "$label expected $expected got $actual")

    @Test
    fun octaveUpKeepsDuration() {
        val result = render { it.copy(pitchSemitones = 12.0) }
        assertEquals(2.0, result.seconds, 1e-3)
        assertNear(880.0, result.frequency, 0.03, "frequency")
    }

    @Test
    fun octaveDownKeepsDuration() {
        val result = render { it.copy(pitchSemitones = -12.0) }
        assertEquals(2.0, result.seconds, 1e-3)
        assertNear(220.0, result.frequency, 0.03, "frequency")
    }

    @Test
    fun pitchAndSpeedAreIndependent() {
        val result = render { it.copy(speed = 1.5, pitchSemitones = 7.0) }
        assertEquals(2.0 / 1.5, result.seconds, 1e-3)
        assertNear(440.0 * 2.0.pow(7.0 / 12), result.frequency, 0.03, "frequency")
    }

    @Test
    fun lockedSpeedKeepsPitch() {
        val faster = render { it.copy(speed = 1.25) }
        assertEquals(2.0 / 1.25, faster.seconds, 1e-3)
        assertNear(440.0, faster.frequency, 0.03, "faster")
        val slower = render { it.copy(speed = 0.8) }
        assertEquals(2.0 / 0.8, slower.seconds, 1e-3)
        assertNear(440.0, slower.frequency, 0.03, "slower")
    }

    // Aligned grains avoid comb dropouts
    @Test
    fun shiftedToneKeepsSteadyLevel() {
        val samples = render { it.copy(pitchSemitones = 5.0) }.samples
        val block = sampleRate / 100
        val levels = (0 until samples.size / block).map { b ->
            sqrt((0 until block).sumOf { (samples[b * block + it] * samples[b * block + it]).toDouble() } / block)
        }
        val mean = levels.average()
        assertTrue(levels.all { abs(it - mean) < mean * 0.2 }, "level swings ${levels.min()}..${levels.max()} mean $mean")
    }

    @Test
    fun unlockedSpeedStillShiftsPitch() {
        val result = render { it.copy(speed = 1.5, keepPitch = false) }
        assertEquals(2.0 / 1.5, result.seconds, 1e-3)
        assertNear(660.0, result.frequency, 0.02, "frequency")
    }
}
