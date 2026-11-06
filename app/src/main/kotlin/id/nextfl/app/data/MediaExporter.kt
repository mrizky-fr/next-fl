package id.nextfl.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/** Pending MediaStore item. */
class ExportTarget(val uri: Uri, val displayName: String, val mimeType: String, val location: String)

/** Publishes exports to Music and Movies. */
class MediaExporter(private val resolver: ContentResolver) {

    fun createAudio(displayName: String, mimeType: String): ExportTarget = insert(
        collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        directory = Environment.DIRECTORY_MUSIC,
        displayName = displayName,
        mimeType = mimeType,
    )

    fun createVideo(displayName: String): ExportTarget = insert(
        collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        directory = Environment.DIRECTORY_MOVIES,
        displayName = displayName,
        mimeType = MP4_MIME,
    )

    fun openStream(target: ExportTarget): OutputStream =
        resolver.openOutputStream(target.uri) ?: throw IOException("Tidak bisa menulis file export")

    // Muxer needs seekable descriptor
    fun openDescriptor(target: ExportTarget): ParcelFileDescriptor =
        resolver.openFileDescriptor(target.uri, "rw") ?: throw IOException("Tidak bisa menulis file export")

    fun publish(target: ExportTarget) {
        resolver.update(target.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    fun discard(target: ExportTarget) {
        runCatching { resolver.delete(target.uri, null, null) }
    }

    fun shareIntent(uri: Uri, mimeType: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Bagikan hasil").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun insert(collection: Uri, directory: String, displayName: String, mimeType: String): ExportTarget {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Tidak bisa membuat file export")
        return ExportTarget(uri, displayName, mimeType, "$directory/$FOLDER")
    }

    companion object {
        const val FOLDER = "NextFL"
        const val WAV_MIME = "audio/wav"
        const val MP3_MIME = "audio/mpeg"
        const val MP4_MIME = "video/mp4"
    }
}
