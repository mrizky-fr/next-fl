package id.nextfl.core.engine

import id.nextfl.core.TestSignals
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.FxChain
import id.nextfl.core.pcm.ArrayStereoSink
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealtimeSafetyTest {
    private val rate = 44_100
    private val tone = TestSignals.stereo(TestSignals.sine(440.0, 2.0, rate, 0.4f), rate)
    private val assets = mapOf("tone" to AudioAsset("tone", "Tone", 2.0, null))
    private val resolver = SourceResolver { assetId -> if (assetId == "tone") tone else null }

    private companion object {
        const val BLOCKS = 60
    }

    private fun rms(data: FloatArray, from: Int, to: Int) =
        sqrt((from until to).sumOf { (data[it] * data[it]).toDouble() } / (to - from))

    @Test
    fun trackProcessorRecoversFromNaN() {
        val frames = 64
        val processor = TrackProcessor(rate, frames)
        val params = TrackParams("track", FxChain(), 1f, 0f)
        processor.left.fill(Float.NaN, 0, frames)
        processor.process(params, frames)
        assertEquals(0f, processor.peak)
        processor.left.fill(0.5f, 0, frames)
        processor.right.fill(0.5f, 0, frames)
        processor.process(params, frames)
        assertTrue(processor.peak.isFinite() && processor.peak > 0f, "peak ${processor.peak}")
    }

    // Same track ids, like the app
    private val base = Defaults.project().copy(bpm = 120.0)

    private fun pitched(semitones: Double): RenderGraph {
        val track = base.tracks[0]
        val clip = Defaults.clip(track.id, "tone", "Tone", 0.0, 2.0).copy(id = "clip", pitchSemitones = semitones)
        return RenderGraphBuilder.build(base.copy(tracks = listOf(track), clips = listOf(clip)), assets, rate, resolver)
    }

    // Slider drag swaps graphs mid-grain
    @Test
    fun pitchDragDoesNotClick() {
        val block = 512
        val engine = MixEngine(rate, block)
        val graphs = listOf(pitched(3.0), pitched(7.0))
        engine.setGraph(graphs[0])
        engine.resetOutputClock(0)
        val out = FloatArray(block * 2)
        val left = FloatArray(block * BLOCKS)
        for (index in 0 until BLOCKS) {
            engine.setGraph(graphs[index % 2])
            engine.renderInterleaved(out, block)
            for (i in 0 until block) left[index * block + i] = out[2 * i]
        }
        var jump = 0f
        for (i in rate / 10 until left.size) jump = maxOf(jump, kotlin.math.abs(left[i] - left[i - 1]))
        assertTrue(jump < 0.12f, "pitch drag click $jump")
    }

    // First grain used to fade in
    @Test
    fun pitchedClipStartsAtFullLevel() {
        val graph = pitched(5.0)
        val sink = ArrayStereoSink()
        OfflineRenderer.render(graph, rate, sink)
        val source = sink.toSource(rate)
        val output = FloatArray(source.frames) { source.left(it) }
        val early = rms(output, 400, 800)
        val steady = rms(output, 20_000, 40_000)
        assertTrue(early > steady * 0.8, "early $early steady $steady")
    }
}
