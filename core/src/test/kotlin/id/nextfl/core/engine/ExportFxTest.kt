package id.nextfl.core.engine

import id.nextfl.core.TestSignals
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.CompressorSettings
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.FxChain
import id.nextfl.core.model.GraphicEqSettings
import id.nextfl.core.model.LevellerSettings
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.model.StereoizerSettings
import id.nextfl.core.model.Track
import id.nextfl.core.pcm.ArrayStereoSink
import id.nextfl.core.pcm.PcmSource
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/** Mixer settings must reach exports. */
class ExportFxTest {
    private val rate = 44_100
    private val tone = TestSignals.stereo(TestSignals.sine(440.0, 2.0, rate, 0.4f), rate)
    private val assets = mapOf("tone" to AudioAsset("tone", "Tone", 2.0, null))
    private val resolver = object : SourceResolver {
        override fun original(assetId: String): PcmSource? = if (assetId == "tone") tone else null
    }

    private fun export(edit: (ProjectDoc, Track) -> ProjectDoc = { doc, _ -> doc }): Pair<FloatArray, FloatArray> {
        val base = Defaults.project().copy(bpm = 120.0)
        val track = base.tracks[0]
        val doc = base.copy(tracks = listOf(track), clips = listOf(Defaults.clip(track.id, "tone", "Tone", 0.0, 2.0)))
        val sink = ArrayStereoSink()
        OfflineRenderer.render(RenderGraphBuilder.build(edit(doc, track), assets, rate, resolver), rate, sink)
        val source = sink.toSource(rate)
        return FloatArray(source.frames) { source.left(it) } to FloatArray(source.frames) { source.right(it) }
    }

    private fun ProjectDoc.withTrack(transform: (Track) -> Track) = copy(tracks = tracks.map(transform))

    private fun rms(data: FloatArray, from: Double, to: Double): Double {
        val range = (from * rate).toInt() until minOf(data.size, (to * rate).toInt())
        return sqrt(range.sumOf { (data[it] * data[it]).toDouble() } / range.count())
    }

    private val plain by lazy { export() }

    @Test
    fun trackVolumeAndPanAreRendered() {
        val (left, right) = export { doc, _ -> doc.withTrack { it.copy(volumeDb = -12f, pan = 1f) } }
        assertTrue(rms(left, 0.2, 0.8) < 1e-4, "pan right should silence left")
        val ratio = rms(right, 0.2, 0.8) / rms(plain.second, 0.2, 0.8)
        assertTrue(ratio in 0.2..0.3, "volume -12 dB ratio $ratio")
    }

    @Test
    fun trackFxChainIsRendered() {
        val chain = FxChain(
            reverb = ReverbSettings(enabled = true, mix = 0.5f, decay = 2f),
            leveller = LevellerSettings(enabled = true, gainDb = -6f),
        )
        val (left, _) = export { doc, _ -> doc.withTrack { it.withFx(chain) } }
        assertTrue(left.size > plain.first.size, "reverb tail must extend export")
        assertTrue(rms(left, 2.1, 2.6) > 1e-3, "reverb tail missing after clip end")
    }

    @Test
    fun stereoizerAndGraphicEqAreRendered() {
        val chain = FxChain(
            stereoizer = StereoizerSettings(enabled = true, strength = 1f, focus = 0f, lrDelay = 0.5f),
            graphicEq = GraphicEqSettings(enabled = true, gains = List(GraphicEqSettings.BANDS) { if (it == 2) 12f else 0f }),
        )
        val (left, right) = export { doc, _ -> doc.withTrack { it.withFx(chain) } }
        val difference = (rate / 5 until rate).sumOf { ((left[it] - right[it]) * (left[it] - right[it])).toDouble() }
        assertTrue(difference > 1.0, "stereoizer left right difference $difference")
        assertTrue(rms(left, 0.2, 0.8) > rms(plain.first, 0.2, 0.8) * 1.5, "graphic EQ boost near 375 Hz missing")
    }

    @Test
    fun compressorIsRenderedOnTrackAndMaster() {
        val squash = FxChain(compressor = CompressorSettings(enabled = true, thresholdDb = -30f, ratio = 10f, kneeDb = 0f, attackMs = 1f))
        val (track, _) = export { doc, _ -> doc.withTrack { it.withFx(squash) } }
        val trackRatio = rms(track, 0.2, 0.8) / rms(plain.first, 0.2, 0.8)
        assertTrue(trackRatio < 0.3, "track compressor missing, ratio $trackRatio")
        val (master, _) = export { doc, _ -> doc.copy(masterFx = squash) }
        val masterRatio = rms(master, 0.2, 0.8) / rms(plain.first, 0.2, 0.8)
        assertTrue(masterRatio < 0.3, "master compressor missing, ratio $masterRatio")
        val neutralChain = FxChain(compressor = CompressorSettings(enabled = true))
        val (neutral, _) = export { doc, _ -> doc.withTrack { it.withFx(neutralChain) } }
        val unity = rms(neutral, 0.2, 0.8) / rms(plain.first, 0.2, 0.8)
        assertTrue(unity in 0.99..1.01, "default compressor must be unity, ratio $unity")
    }

    @Test
    fun masterVolumeAndMasterFxAreRendered() {
        val (quiet, _) = export { doc, _ -> doc.copy(masterDb = -12f) }
        val quietRatio = rms(quiet, 0.2, 0.8) / rms(plain.first, 0.2, 0.8)
        assertTrue(quietRatio in 0.2..0.3, "master -12 dB ratio $quietRatio")
        val (boosted, _) = export { doc, _ -> doc.copy(masterDb = -12f, masterFx = FxChain(leveller = LevellerSettings(enabled = true, gainDb = 6f))) }
        val boostRatio = rms(boosted, 0.2, 0.8) / rms(quiet, 0.2, 0.8)
        assertTrue(boostRatio in 1.8..2.2, "master leveller +6 dB on top of fader, ratio $boostRatio")
    }
}
