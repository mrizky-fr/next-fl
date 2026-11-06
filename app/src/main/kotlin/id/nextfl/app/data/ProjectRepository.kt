package id.nextfl.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.VisualSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import kotlin.math.max

@Serializable
data class SavedProject(
    val version: Int = CURRENT_VERSION,
    val doc: ProjectDoc,
    val assets: List<AudioAsset>,
    val visual: VisualSettings,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

class ProjectRepository(private val paths: StoragePaths, private val resolver: ContentResolver) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): SavedProject? = withContext(Dispatchers.IO) {
        val file = paths.projectFile
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(SavedProject.serializer(), file.readText())
        } catch (error: SerializationException) {
            android.util.Log.w(TAG, "Saved project unreadable, starting fresh", error)
            keepBroken(file)
        } catch (error: IllegalArgumentException) {
            android.util.Log.w(TAG, "Saved project invalid, starting fresh", error)
            keepBroken(file)
        }
    }

    // Autosave must not erase it
    private fun keepBroken(file: File): SavedProject? {
        runCatching { file.copyTo(File(file.path + BROKEN_SUFFIX), overwrite = true) }
        return null
    }

    // Rename keeps file crash-safe
    suspend fun save(project: SavedProject) = withContext(Dispatchers.IO) {
        val temp = File(paths.projectFile.path + ".tmp")
        temp.writeText(json.encodeToString(SavedProject.serializer(), project))
        if (!temp.renameTo(paths.projectFile)) {
            paths.projectFile.delete()
            temp.renameTo(paths.projectFile)
        }
    }

    // Old image kept until valid
    suspend fun importImage(kind: VisualImage, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val target = paths.image(kind)
        val temp = File(target.path + ".tmp")
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("Gambar tidak bisa dibuka")
            val bitmap = decodeImage(temp) ?: throw IOException("Gambar tidak valid")
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("Gambar tidak bisa disimpan")
            }
            bitmap
        } finally {
            temp.delete()
        }
    }

    suspend fun loadImage(kind: VisualImage): Bitmap? = withContext(Dispatchers.IO) {
        paths.image(kind).takeIf { it.exists() }?.let(::decodeImage)
    }

    fun deleteImage(kind: VisualImage) {
        paths.image(kind).delete()
    }

    private fun decodeImage(file: File): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = MAX_IMAGE_EDGE.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize(scaled(info.size.width, scale), scaled(info.size.height, scale))
        }
    }.getOrNull()

    private fun scaled(edge: Int, scale: Float): Int = (edge * scale).toInt().coerceAtLeast(1)

    private companion object {
        const val TAG = "ProjectRepository"
        const val MAX_IMAGE_EDGE = 2048
        const val BROKEN_SUFFIX = ".broken"
    }
}
