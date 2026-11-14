package id.nextfl.app.data

import android.graphics.Bitmap
import java.io.File

enum class VisualImage(val fileName: String, val collabDir: String) {
    BACKGROUND("background.img", "collab-backgrounds"),
    LOGO("logo.img", "collab-logos"),
}

/** Extra collab image, id is file name. */
class CollabImage(val id: String, val bitmap: Bitmap)

// Main image first, then extras
fun rotationImages(main: Bitmap?, extras: List<CollabImage>): List<Bitmap> = listOfNotNull(main) + extras.map { it.bitmap }

/** All on-device file locations. */
class StoragePaths(filesDir: File, cacheDir: File) {
    private val assetsDir = File(filesDir, "assets").apply { mkdirs() }
    private val imagesDir = File(filesDir, "images").apply { mkdirs() }
    val projectFile = File(filesDir, "project.json")
    val renderMix = File(cacheDir, "render-mix.pcm")
    val renderMp3 = File(cacheDir, "render-mix.mp3")

    init {
        // Old offline stretch cache
        File(cacheDir, "stretch").deleteRecursively()
    }

    fun asset(assetId: String) = File(assetsDir, "$assetId.pcm")

    fun image(kind: VisualImage) = File(imagesDir, kind.fileName)

    fun collabDir(kind: VisualImage) = File(imagesDir, kind.collabDir).apply { mkdirs() }

    fun collabImage(kind: VisualImage, id: String) = File(collabDir(kind), "$id$IMAGE_SUFFIX")

    // Ids are padded, so name order
    fun collabImages(kind: VisualImage): List<File> =
        collabDir(kind).listFiles { file -> file.name.endsWith(IMAGE_SUFFIX) }.orEmpty().sortedBy { it.name }

    fun clearAssets() {
        assetsDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val IMAGE_SUFFIX = ".img"
    }
}
