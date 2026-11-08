package id.nextfl.app.data

import android.graphics.Bitmap
import id.nextfl.app.platform.ImageCodec
import id.nextfl.app.platform.PickedFile
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

class ProjectRepository(private val paths: StoragePaths, private val images: ImageCodec) {
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
    suspend fun importImage(kind: VisualImage, file: PickedFile): Bitmap = withContext(Dispatchers.IO) {
        val target = paths.image(kind)
        val temp = File(target.path + ".tmp")
        try {
            file.open().use { input -> temp.outputStream().use { input.copyTo(it) } }
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

    private fun decodeImage(file: File): Bitmap? = runCatching { images.decode(file, MAX_IMAGE_EDGE) }.getOrNull()

    private companion object {
        const val TAG = "ProjectRepository"
        const val MAX_IMAGE_EDGE = 2048
        const val BROKEN_SUFFIX = ".broken"
    }
}
