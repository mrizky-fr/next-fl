package id.nextfl.app.platform

import android.graphics.Bitmap
import android.graphics.Typeface
import id.nextfl.app.audio.AudioDevice
import id.nextfl.app.audio.DecodedAudio
import id.nextfl.core.model.AspectRatio
import id.nextfl.core.model.VisualSettings
import java.io.File
import java.io.OutputStream

/** Everything that differs per OS. */
interface Platform {
    val filesDir: File
    val cacheDir: File
    val audioDevice: AudioDevice
    val decoder: AudioDecoding
    val exporter: Exporter
    val images: ImageCodec
    val canvasFonts: CanvasFonts

    /** Null until the OS supports it. */
    val video: VideoExporter?

    /** Toast action after export. */
    val shareLabel: String

    /** False when nothing can share. */
    fun share(target: ExportTarget): Boolean
}

/** Inter for android.graphics canvases. */
class CanvasFonts(val semibold: Typeface, val bold: Typeface)

fun interface AudioDecoding {
    suspend fun decode(file: PickedFile, target: File, onProgress: (Float) -> Unit): DecodedAudio
}

fun interface ImageCodec {
    /** Null when unreadable. */
    fun decode(file: File, maxEdge: Int): Bitmap?
}

/** Export file before it is published. */
interface ExportTarget {
    val displayName: String
    val mimeType: String
    val location: String
}

interface Exporter {
    fun createAudio(displayName: String, mimeType: String): ExportTarget
    fun openStream(target: ExportTarget): OutputStream
    fun publish(target: ExportTarget)
    fun discard(target: ExportTarget)

    companion object {
        const val FOLDER = "NextFL"
        const val WAV_MIME = "audio/wav"
        const val MP3_MIME = "audio/mpeg"
        const val MP4_MIME = "video/mp4"
    }
}

class VideoRequest(
    val displayName: String,
    val mix: File,
    val aspect: AspectRatio,
    val settings: VisualSettings,
    val backgrounds: List<Bitmap>,
    val logos: List<Bitmap>,
)

fun interface VideoExporter {
    suspend fun export(
        request: VideoRequest,
        track: (ExportTarget) -> ExportTarget,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ExportTarget
}
