package id.nextfl.app.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.Exporter
import id.nextfl.app.platform.Exporter.Companion.FOLDER
import id.nextfl.app.platform.Exporter.Companion.MP4_MIME
import java.io.IOException
import java.io.OutputStream

/** Pending MediaStore item. */
class MediaStoreTarget(val uri: Uri, override val displayName: String, override val mimeType: String, override val location: String) : ExportTarget

/** Publishes exports to Music and Movies. */
class MediaExporter(private val resolver: ContentResolver) : Exporter {

    override fun createAudio(displayName: String, mimeType: String): MediaStoreTarget = insert(
        collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        directory = Environment.DIRECTORY_MUSIC,
        displayName = displayName,
        mimeType = mimeType,
    )

    fun createVideo(displayName: String): MediaStoreTarget = insert(
        collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        directory = Environment.DIRECTORY_MOVIES,
        displayName = displayName,
        mimeType = MP4_MIME,
    )

    override fun openStream(target: ExportTarget): OutputStream =
        resolver.openOutputStream(uri(target)) ?: throw IOException("Tidak bisa menulis file export")

    // Muxer needs seekable descriptor
    fun openDescriptor(target: ExportTarget): ParcelFileDescriptor =
        resolver.openFileDescriptor(uri(target), "rw") ?: throw IOException("Tidak bisa menulis file export")

    override fun publish(target: ExportTarget) {
        resolver.update(uri(target), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    override fun discard(target: ExportTarget) {
        runCatching { resolver.delete(uri(target), null, null) }
    }

    private fun uri(target: ExportTarget): Uri = (target as MediaStoreTarget).uri

    fun shareIntent(uri: Uri, mimeType: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Bagikan hasil").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun insert(collection: Uri, directory: String, displayName: String, mimeType: String): MediaStoreTarget {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Tidak bisa membuat file export")
        return MediaStoreTarget(uri, displayName, mimeType, "$directory/$FOLDER")
    }
}
