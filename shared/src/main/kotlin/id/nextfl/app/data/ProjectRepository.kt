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

    suspend fun importImage(kind: VisualImage, file: PickedFile): Bitmap = withContext(Dispatchers.IO) {
        importTo(paths.image(kind), file, MAX_IMAGE_EDGE)
    }

    suspend fun loadImage(kind: VisualImage): Bitmap? = withContext(Dispatchers.IO) {
        paths.image(kind).takeIf { it.exists() }?.let { decodeImage(it, MAX_IMAGE_EDGE) }
    }

    fun deleteImage(kind: VisualImage) {
        paths.image(kind).delete()
    }

    suspend fun importCollab(kind: VisualImage, file: PickedFile): CollabImage = withContext(Dispatchers.IO) {
        val id = nextCollabId(kind)
        CollabImage(id, importTo(paths.collabImage(kind, id), file, collabEdge(kind)))
    }

    // Unreadable files are skipped
    suspend fun loadCollab(kind: VisualImage): List<CollabImage> = withContext(Dispatchers.IO) {
        paths.collabImages(kind).take(VisualSettings.MAX_COLLAB).mapNotNull { file ->
            decodeImage(file, collabEdge(kind))?.let { CollabImage(file.name.removeSuffix(StoragePaths.IMAGE_SUFFIX), it) }
        }
    }

    fun deleteCollab(kind: VisualImage, id: String) {
        paths.collabImage(kind, id).delete()
    }

    // Old image kept until valid
    private fun importTo(target: File, file: PickedFile, maxEdge: Int): Bitmap {
        val temp = File(target.path + ".tmp")
        try {
            file.open().use { input -> temp.outputStream().use { input.copyTo(it) } }
            val bitmap = decodeImage(temp, maxEdge) ?: throw IOException("Gambar tidak valid")
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("Gambar tidak bisa disimpan")
            }
            return bitmap
        } finally {
            temp.delete()
        }
    }

    // Time based, keeps add order
    private fun nextCollabId(kind: VisualImage): String {
        var stamp = System.currentTimeMillis()
        while (paths.collabImage(kind, collabId(stamp)).exists()) stamp++
        return collabId(stamp)
    }

    private fun collabId(stamp: Long) = stamp.toString().padStart(ID_DIGITS, '0')

    private fun collabEdge(kind: VisualImage) = if (kind == VisualImage.LOGO) MAX_LOGO_EDGE else MAX_IMAGE_EDGE

    private fun decodeImage(file: File, maxEdge: Int): Bitmap? = runCatching { images.decode(file, maxEdge) }.getOrNull()

    private companion object {
        const val TAG = "ProjectRepository"
        const val MAX_IMAGE_EDGE = 2048

        // Badge is small on screen
        const val MAX_LOGO_EDGE = 1024
        const val ID_DIGITS = 15
        const val BROKEN_SUFFIX = ".broken"
    }
}
