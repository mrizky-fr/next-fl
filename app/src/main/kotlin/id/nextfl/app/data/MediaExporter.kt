package id.nextfl.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

class ExportTarget(val uri: Uri, val stream: OutputStream, val displayName: String)

/** Publishes exports to Music. */
class MediaExporter(private val resolver: ContentResolver) {

    fun createAudio(displayName: String, mimeType: String): ExportTarget {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Tidak bisa membuat file export")
        val stream = resolver.openOutputStream(uri) ?: run {
            resolver.delete(uri, null, null)
            throw IOException("Tidak bisa menulis file export")
        }
        return ExportTarget(uri, stream, displayName)
    }

    fun publish(target: ExportTarget) {
        resolver.update(target.uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }

    fun discard(target: ExportTarget) {
        runCatching { target.stream.close() }
        resolver.delete(target.uri, null, null)
    }

    fun shareIntent(uri: Uri, mimeType: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Bagikan hasil").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val FOLDER = "NextFL"
        const val WAV_MIME = "audio/wav"
    }
}
