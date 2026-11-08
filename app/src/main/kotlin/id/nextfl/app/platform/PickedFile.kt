package id.nextfl.app.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream

/** Document chosen in the system picker. */
class PickedFile(val uri: Uri, private val resolver: ContentResolver) {
    val name: String by lazy { queryName() ?: "Audio" }

    fun open(): InputStream = resolver.openInputStream(uri) ?: throw IOException("File tidak bisa dibuka")

    private fun queryName(): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
