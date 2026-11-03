package id.nextfl.app.video

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import id.nextfl.app.visual.PulseRingTemplate
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.PcmSource
import java.io.FileDescriptor
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Template visuals plus mix into MP4. */
class VideoRenderer(
    private val plan: VideoPlan,
    private val audio: PcmSource,
    private val settings: VisualSettings,
    private val background: Bitmap?,
    private val logo: Bitmap?,
    private val typeface: Typeface,
) {
    private val info = MediaCodec.BufferInfo()
    private val analyzer = SpectrumAnalyzer(audio.sampleRate)
    private val window = FloatArray(analyzer.fftSize)
    private val lookaheadFrames = (LOOKAHEAD_SECONDS * audio.sampleRate).roundToInt()
    private var audioFrame = 0
    private var audioDone = false
    private val ended = HashSet<MuxTrack>()

    /** Returns false when cancelled. */
    fun render(output: FileDescriptor, onProgress: (Float) -> Unit, isCancelled: () -> Boolean): Boolean {
        val video = MediaCodec.createByCodecName(plan.codecName)
        val aac = MediaCodec.createEncoderByType(AAC_MIME)
        var muxer: Mp4Muxer? = null
        var input: Surface? = null
        var bridge: EncoderSurfaceBridge? = null
        try {
            muxer = Mp4Muxer(output, plan.rotate)
            video.configure(videoFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = video.createInputSurface()
            video.start()
            aac.configure(audioFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aac.start()
            bridge = EncoderSurfaceBridge(input, plan.width, plan.height, plan.encodeWidth, plan.encodeHeight, plan.rotate)
            if (!renderFrames(bridge, video, aac, muxer, onProgress, isCancelled)) return false
            video.signalEndOfInputStream()
            drain(video, MuxTrack.VIDEO, muxer, untilEnd = true)
            feedAudio(aac, muxer, audio.frames)
            drain(aac, MuxTrack.AUDIO, muxer, untilEnd = true)
            muxer.finish()
            return true
        } finally {
            bridge?.close()
            runCatching { video.stop() }
            runCatching { aac.stop() }
            video.release()
            aac.release()
            input?.release()
            muxer?.release()
        }
    }

    private fun renderFrames(
        bridge: EncoderSurfaceBridge,
        video: MediaCodec,
        aac: MediaCodec,
        muxer: Mp4Muxer,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val template = PulseRingTemplate(typeface).apply { setImages(background, logo) }
        val total = ceil(audio.durationSeconds * VideoPlan.FPS).toInt()
        val dt = 1f / VideoPlan.FPS
        for (index in 0 until total) {
            if (isCancelled()) return false
            val sample = (index.toLong() * audio.sampleRate / VideoPlan.FPS).toInt()
            val frame = analyzer.analyze(fillWindow(sample + lookaheadFrames), dt, settings.sensitivity)
            val canvas = bridge.canvasSurface.lockHardwareCanvas()
            canvas.drawColor(Color.BLACK)
            template.draw(canvas, plan.width, plan.height, frame, dt, settings)
            bridge.canvasSurface.unlockCanvasAndPost(canvas)
            bridge.publish(index * NANOS_PER_SECOND / VideoPlan.FPS)
            drain(video, MuxTrack.VIDEO, muxer, untilEnd = false)
            feedAudio(aac, muxer, ((index + 1L) * audio.sampleRate / VideoPlan.FPS).toInt())
            onProgress((index + 1f) / total)
        }
        return true
    }

    // Mono mix like live tap
    private fun fillWindow(endExclusive: Int): FloatArray {
        val start = endExclusive - window.size
        for (i in window.indices) {
            val f = start + i
            window[i] = (audio.left(f) + audio.right(f)) * 0.5f
        }
        return window
    }

    // Feeds PCM until frame, EOS at end
    private fun feedAudio(aac: MediaCodec, muxer: Mp4Muxer, untilFrame: Int) {
        val target = min(untilFrame, audio.frames)
        while (!audioDone && (audioFrame < target || target == audio.frames)) {
            val index = aac.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) {
                drain(aac, MuxTrack.AUDIO, muxer, untilEnd = false)
                continue
            }
            val buffer = checkNotNull(aac.getInputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
            buffer.clear()
            val count = min((target - audioFrame), buffer.capacity() / BYTES_PER_FRAME)
            for (i in 0 until count) {
                buffer.putShort(toPcm16(audio.left(audioFrame + i)))
                buffer.putShort(toPcm16(audio.right(audioFrame + i)))
            }
            val ptsUs = audioFrame.toLong() * MICROS_PER_SECOND / audio.sampleRate
            audioFrame += count
            val end = audioFrame >= audio.frames
            aac.queueInputBuffer(index, 0, count * BYTES_PER_FRAME, ptsUs, if (end) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
            audioDone = end
            drain(aac, MuxTrack.AUDIO, muxer, untilEnd = false)
            if (!end && audioFrame >= target) return
        }
    }

    private fun drain(codec: MediaCodec, track: MuxTrack, muxer: Mp4Muxer, untilEnd: Boolean) {
        if (track in ended) return
        var idle = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEnd) OUTPUT_TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd) return
                    check(++idle < MAX_IDLE_POLLS) { "Encoder berhenti merespons" }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> muxer.addTrack(track, codec.outputFormat)
                index >= 0 -> {
                    idle = 0
                    val buffer = checkNotNull(codec.getOutputBuffer(index))
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0) muxer.write(track, buffer, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        ended.add(track)
                        return
                    }
                }
            }
        }
    }

    private fun videoFormat() = MediaFormat.createVideoFormat(VideoPlan.MIME, plan.encodeWidth, plan.encodeHeight).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, plan.bitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, VideoPlan.FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEYFRAME_SECONDS)
    }

    private fun audioFormat() = MediaFormat.createAudioFormat(AAC_MIME, audio.sampleRate, CHANNELS).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_INPUT_BYTES)
    }

    private fun toPcm16(value: Float): Short = (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()

    private companion object {
        const val AAC_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        const val AAC_BITRATE = 192_000
        const val AAC_INPUT_BYTES = 16_384
        const val CHANNELS = 2
        const val BYTES_PER_FRAME = 4
        const val KEYFRAME_SECONDS = 1
        const val LOOKAHEAD_SECONDS = 0.06
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 1000
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
