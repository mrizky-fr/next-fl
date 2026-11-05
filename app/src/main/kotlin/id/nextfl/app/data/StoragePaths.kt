package id.nextfl.app.data

import java.io.File

enum class VisualImage(val fileName: String) { BACKGROUND("background.img"), LOGO("logo.img") }

/** All on-device file locations. */
class StoragePaths(filesDir: File, cacheDir: File) {
    private val assetsDir = File(filesDir, "assets").apply { mkdirs() }
    private val imagesDir = File(filesDir, "images").apply { mkdirs() }
    val projectFile = File(filesDir, "project.json")
    val renderMix = File(cacheDir, "render-mix.pcm")

    init {
        // Old offline stretch cache
        File(cacheDir, "stretch").deleteRecursively()
    }

    fun asset(assetId: String) = File(assetsDir, "$assetId.pcm")

    fun image(kind: VisualImage) = File(imagesDir, kind.fileName)

    fun clearAssets() {
        assetsDir.listFiles()?.forEach { it.delete() }
    }
}
