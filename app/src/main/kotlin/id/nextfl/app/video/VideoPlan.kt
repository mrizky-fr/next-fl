package id.nextfl.app.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import id.nextfl.core.model.AspectRatio
import java.io.IOException

/** Frame size and encoder choice. */
class VideoPlan(
    val codecName: String,
    val width: Int,
    val height: Int,
    val rotate: Boolean,
    val bitrate: Int,
) {
    // Rotated plans encode landscape
    val encodeWidth get() = if (rotate) height else width
    val encodeHeight get() = if (rotate) width else height

    companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val FPS = 30
        private const val BITS_PER_PIXEL = 0.12
        private val SHORT_SIDES = intArrayOf(1080, 720)

        fun resolve(aspect: AspectRatio): VideoPlan {
            for (codec in encoders()) {
                val caps = codec.getCapabilitiesForType(MIME).videoCapabilities ?: continue
                for (short in SHORT_SIDES) {
                    val (w, h) = frameSize(aspect, short)
                    val rotate = when {
                        caps.areSizeAndRateSupported(w, h, FPS.toDouble()) -> false
                        caps.areSizeAndRateSupported(h, w, FPS.toDouble()) -> true
                        else -> continue
                    }
                    val bitrate = caps.bitrateRange.clamp((w * h * FPS * BITS_PER_PIXEL).toInt())
                    return VideoPlan(codec.name, w, h, rotate, bitrate)
                }
            }
            throw IOException("Perangkat tidak mendukung encoder video")
        }

        fun frameSize(aspect: AspectRatio, short: Int): Pair<Int, Int> {
            val long = even(short * maxOf(aspect.width, aspect.height) / minOf(aspect.width, aspect.height))
            return if (aspect.width >= aspect.height) long to short else short to long
        }

        // Hardware encoders first
        private fun encoders(): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) } }
            .sortedBy { if (it.isHardwareAccelerated) 0 else 1 }

        private fun even(value: Int) = value and 1.inv()
    }
}
