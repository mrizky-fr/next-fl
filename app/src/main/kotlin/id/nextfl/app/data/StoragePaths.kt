package id.nextfl.app.data

import java.io.File
import java.util.Locale

enum class VisualImage(val fileName: String) { BACKGROUND("background.img"), LOGO("logo.img") }

/** All on-device file locations. */
class StoragePaths(filesDir: File, cacheDir: File) {
    private val assetsDir = File(filesDir, "assets").apply { mkdirs() }
    private val stretchDir = File(cacheDir, "stretch").apply { mkdirs() }
    private val imagesDir = File(filesDir, "images").apply { mkdirs() }
    val projectFile = File(filesDir, "project.json")
    val renderMix = File(cacheDir, "render-mix.pcm")

    fun asset(assetId: String) = File(assetsDir, "$assetId.pcm")

    fun stretched(assetId: String, rate: Double) =
        File(stretchDir, "${assetId}@${String.format(Locale.ROOT, "%.4f", rate)}.pcm")

    fun stretchedFiles(): List<File> = stretchDir.listFiles()?.toList().orEmpty()

    fun image(kind: VisualImage) = File(imagesDir, kind.fileName)

    fun clearAssets() {
        assetsDir.listFiles()?.forEach { it.delete() }
        stretchDir.listFiles()?.forEach { it.delete() }
    }
}
