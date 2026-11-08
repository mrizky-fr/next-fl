package id.nextfl.desktop.data

import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.Exporter
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Unpublished file in Music or Videos. */
class FileTarget(val file: File, val part: File, override val mimeType: String, override val location: String) : ExportTarget {
    override val displayName: String get() = file.name
}

/** Writes exports under the user's folders. */
class DesktopExporter(private val home: File) : Exporter {

    override fun createAudio(displayName: String, mimeType: String): FileTarget = create(MUSIC, displayName, mimeType)

    fun createVideo(displayName: String): FileTarget = create(VIDEOS, displayName, Exporter.MP4_MIME)

    override fun openStream(target: ExportTarget): OutputStream = fileTarget(target).part.outputStream()

    // Rename makes file appear complete
    override fun publish(target: ExportTarget) {
        val file = fileTarget(target)
        if (!file.part.renameTo(file.file)) throw IOException("File export tidak bisa disimpan")
    }

    override fun discard(target: ExportTarget) {
        fileTarget(target).part.delete()
    }

    private fun create(folder: String, displayName: String, mimeType: String): FileTarget {
        val directory = File(File(home, folder), Exporter.FOLDER)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Folder export tidak bisa dibuat")
        val file = unique(directory, displayName)
        return FileTarget(file, File(directory, file.name + PART_SUFFIX), mimeType, "$folder${File.separator}${Exporter.FOLDER}")
    }

    // Same-minute exports get a suffix
    private fun unique(directory: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.')
        val extension = displayName.substringAfterLast('.', "")
        var candidate = File(directory, displayName)
        var index = 1
        while (candidate.exists()) candidate = File(directory, "$base ($index).$extension").also { index++ }
        return candidate
    }

    private fun fileTarget(target: ExportTarget): FileTarget = target as FileTarget

    private companion object {
        const val MUSIC = "Music"
        const val VIDEOS = "Videos"
        const val PART_SUFFIX = ".part"
    }
}
