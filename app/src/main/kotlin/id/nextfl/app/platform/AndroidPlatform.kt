package id.nextfl.app.platform

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import id.nextfl.app.AppContainer
import id.nextfl.app.audio.AndroidAudioDevice
import id.nextfl.app.audio.AudioDecoder
import id.nextfl.app.data.MediaExporter
import id.nextfl.app.data.MediaStoreTarget
import id.nextfl.app.ui.theme.Fonts
import id.nextfl.app.video.AndroidVideoExporter
import java.io.File
import kotlin.math.max

/** Android services behind shared code. */
class AndroidPlatform(private val app: Application) : Platform {
    override val filesDir: File = app.filesDir
    override val cacheDir: File = app.cacheDir
    override val audioDevice = AndroidAudioDevice(app)
    override val decoder = AudioDecoder(app, AppContainer.SAMPLE_RATE)
    override val exporter = MediaExporter(app.contentResolver)
    override val images = ImageCodec(::decodeImage)
    override val canvasFonts by lazy { CanvasFonts(Fonts.semibold(app), Fonts.bold(app)) }
    override val video = AndroidVideoExporter(exporter, AppContainer.SAMPLE_RATE) { canvasFonts.bold }
    override val shareLabel = "Bagikan"

    override fun share(target: ExportTarget): Boolean = try {
        app.startActivity(exporter.shareIntent((target as MediaStoreTarget).uri, target.mimeType))
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Share failed", error)
        false
    }

    private fun decodeImage(file: File, maxEdge: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = maxEdge.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize(scaled(info.size.width, scale), scaled(info.size.height, scale))
        }

    private fun scaled(edge: Int, scale: Float): Int = (edge * scale).toInt().coerceAtLeast(1)

    private companion object {
        const val TAG = "AndroidPlatform"
    }
}
