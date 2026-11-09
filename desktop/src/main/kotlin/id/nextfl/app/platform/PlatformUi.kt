package id.nextfl.app.platform

import android.graphics.Bitmap
import android.graphics.toComposeImageBitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import id.nextfl.desktop.DesktopWindow
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.io.File

private const val TAG = "PlatformUi"
private val AUDIO_EXTENSIONS = listOf("mp3", "wav", "aif", "aiff", "flac", "m4a", "aac", "ogg", "opus")
private val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "bmp")

@Composable
fun rememberAudioPicker(onPicked: (List<PickedFile>) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch {
                val files = pickFiles("Pilih audio", AUDIO_EXTENSIONS, multiple = true)
                if (files.isNotEmpty()) latest(files.map(::PickedFile))
            }
        }
    }
}

@Composable
fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return remember {
        { scope.launch { pickFiles("Pilih gambar", IMAGE_EXTENSIONS, multiple = false).firstOrNull()?.let { latest(PickedFile(it)) } } }
    }
}

// Desktop never sleeps mid-song
@Composable
fun KeepScreenOn(enabled: Boolean) = Unit

fun Bitmap.toImageBitmap(): ImageBitmap = toComposeImageBitmap()

fun Canvas.drawingCanvas(): android.graphics.Canvas = android.graphics.Canvas(nativeCanvas)

// Native Windows dialog, AWT fallback
private suspend fun pickFiles(title: String, extensions: List<String>, multiple: Boolean): List<File> =
    withPickerFallback({ nativeFiles(title, extensions, multiple) }) { awtFiles(title, extensions, multiple) }

private suspend fun nativeFiles(title: String, extensions: List<String>, multiple: Boolean): List<File> {
    val settings = DesktopWindow.frame?.let { FileKitDialogSettings(title = title, parent = FileKitDialogParent.awt(it)) }
        ?: FileKitDialogSettings(title = title)
    val type = FileKitType.File(extensions)
    return if (multiple) {
        FileKit.openFilePicker(type = type, mode = FileKitMode.Multiple(), dialogSettings = settings).orEmpty().map { it.file }
    } else {
        listOfNotNull(FileKit.openFilePicker(type = type, dialogSettings = settings)?.file)
    }
}

// Blocked JNA DLL throws LinkageError
internal suspend fun withPickerFallback(native: suspend () -> List<File>, fallback: () -> List<File>): List<File> = try {
    native()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.w(TAG, "Native picker failed, using AWT", error)
    fallback()
} catch (error: LinkageError) {
    Log.w(TAG, "Native library unavailable, using AWT", error)
    fallback()
}

private fun awtFiles(title: String, extensions: List<String>, multiple: Boolean): List<File> {
    val dialog = FileDialog(DesktopWindow.frame, title, FileDialog.LOAD).apply {
        isMultipleMode = multiple
        file = extensions.joinToString(";") { "*.$it" }
        DesktopWindow.lastDirectory?.let { directory = it }
    }
    dialog.isVisible = true
    val files = dialog.files.toList()
    dialog.directory?.let { DesktopWindow.lastDirectory = it }
    dialog.dispose()
    return files
}
