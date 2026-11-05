package id.nextfl.core.engine

import id.nextfl.core.TestSignals
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Defaults
import id.nextfl.core.pcm.ArrayStereoSink
import id.nextfl.core.pcm.PcmSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTest {
    private val rate = 44_100
    private val tone = TestSignals.stereo(TestSignals.sine(440.0, 2.0, rate, 0.4f), rate)
    private val assets = mapOf("tone" to AudioAsset("tone", "Tone", 2.0, null))

    private val resolver = object : SourceResolver {
        override fun original(assetId: String): PcmSource? = if (assetId == "tone") tone else null
    }

    private fun graph(startBeat: Double = 2.0, mute: Boolean = false): RenderGraph {
        val base = Defaults.project().copy(bpm = 120.0)
        val track = base.tracks[0].copy(mute = mute)
        val clip = Defaults.clip(track.id, "tone", "Tone", startBeat, 2.0)
        val doc = base.copy(tracks = listOf(track), clips = listOf(clip))
        return RenderGraphBuilder.build(doc, assets, rate, resolver)
    }

    private fun render(graph: RenderGraph): FloatArray {
        val sink = ArrayStereoSink()
        OfflineRenderer.render(graph, rate, sink)
        val source = sink.toSource(rate)
        return FloatArray(source.frames) { source.left(it) }
    }

    @Test
    fun clipPlaysAtItsBeatPosition() {
        val output = render(graph())
        assertTrue(output.take(rate - 10).all { it == 0f }, "silent before beat 2 (1 s)")
        val body = output.copyOfRange(rate + 2000, rate + 40_000)
        assertTrue(body.maxOf { abs(it) } > 0.35f)
        assertEquals(440.0, TestSignals.zeroCrossingRate(body, rate), 5.0)
    }

    @Test
    fun loopingRestartsAtSongEnd() {
        val engine = MixEngine(rate, 1024)
        engine.looping = true
        engine.setGraph(graph(startBeat = 0.0))
        engine.resetOutputClock(0)
        val out = FloatArray(2048)
        // Clip lasts 2 s; render 2.5 s
        repeat(rate * 5 / 2 / 1024) { engine.renderInterleaved(out, 1024) }
        assertTrue(engine.songFrame in 1 until rate, "song frame ${engine.songFrame} should wrap past 2 s end")
        assertTrue(out.maxOf { abs(it) } > 0.1f, "clip should play again after loop")
    }

    @Test
    fun mutedTrackIsSilent() {
        assertTrue(render(graph(mute = true)).all { it == 0f })
    }

    @Test
    fun seekMappingFollowsOutputClock() {
        val engine = MixEngine(rate, 512)
        engine.setGraph(graph())
        engine.resetOutputClock(1000)
        val out = FloatArray(1024)
        engine.renderInterleaved(out, 512)
        engine.seek(50_000)
        engine.renderInterleaved(out, 512)
        assertEquals(1100, engine.songFrameAtOutput(100))
        assertEquals(50_010, engine.songFrameAtOutput(522))
    }

    @Test
    fun analyzerFindsBassAndBeats() {
        val loop = TestSignals.drumLoop(120.0, 4.0, rate)
        val analyzer = SpectrumAnalyzer(rate)
        val window = FloatArray(analyzer.fftSize)
        var beats = 0
        var hop = 0
        while (hop + window.size < loop.size) {
            loop.copyInto(window, 0, hop, hop + window.size)
            if (analyzer.analyze(window, 735f / rate, 1f).beat) beats++
            hop += 735
        }
        assertTrue(beats in 6..9, "beats $beats")
    }
}
