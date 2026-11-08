package id.nextfl.desktop

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.fromBytes
import android.graphics.fromEncoded
import id.nextfl.app.AppContainer
import id.nextfl.app.platform.CanvasFonts
import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.ImageCodec
import id.nextfl.app.platform.Platform
import id.nextfl.app.platform.VideoExporter
import id.nextfl.desktop.audio.DesktopAudioDecoder
import id.nextfl.desktop.audio.DesktopAudioDevice
import id.nextfl.desktop.data.DesktopExporter
import id.nextfl.desktop.data.FileTarget
import android.util.Log
import java.io.File
import java.io.IOException

/** Windows services behind shared code. */
class DesktopPlatform : Platform {
    private val home = File(System.getProperty("user.home"))
    override val filesDir: File = appDirectory("APPDATA", "NextFL")
    override val cacheDir: File = File(appDirectory("LOCALAPPDATA", "NextFL"), "cache").apply { mkdirs() }
    override val audioDevice = DesktopAudioDevice()
    override val decoder = DesktopAudioDecoder(AppContainer.SAMPLE_RATE)
    override val exporter = DesktopExporter(home)
    override val images = imageCodec
    override val canvasFonts by lazy { CanvasFonts(loadFont("inter_semibold.ttf"), loadFont("inter_bold.ttf")) }

    // MP4 export comes with ffmpeg
    override val video: VideoExporter? = null
    override val shareLabel = "Buka folder"

    override fun share(target: ExportTarget): Boolean = try {
        ProcessBuilder("explorer.exe", "/select,", (target as FileTarget).file.absolutePath).start()
        true
    } catch (error: IOException) {
        Log.e(TAG, "Explorer failed", error)
        false
    }

    private fun appDirectory(variable: String, child: String): File {
        val root = System.getenv(variable)?.let(::File) ?: File(home, ".nextfl")
        return File(root, child).apply { mkdirs() }
    }

    companion object {
        private const val TAG = "DesktopPlatform"
        val imageCodec = ImageCodec { file, maxEdge -> Bitmap.fromEncoded(file.readBytes(), maxEdge) }

        fun loadFont(resource: String): Typeface {
            val bytes = DesktopPlatform::class.java.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
                ?: throw IOException("Font $resource hilang")
            return Typeface.fromBytes(bytes)
        }
    }
}
