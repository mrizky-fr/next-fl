package id.nextfl.desktop

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import id.nextfl.app.AppContainer
import id.nextfl.app.data.ProjectRepository
import id.nextfl.app.data.SavedProject
import id.nextfl.app.data.StoragePaths
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.platform.PickedFile
import id.nextfl.app.ui.AppRoot
import id.nextfl.app.ui.theme.AppTheme
import id.nextfl.app.visual.PulseRingTemplate
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.VisualSettings
import id.nextfl.desktop.audio.DesktopAudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.io.path.createTempDirectory

/** Headless check of release build. */
object SelfTest {
    private const val READY_TIMEOUT_MS = 15_000L
    private const val SCREEN_WIDTH = 1920
    private const val SCREEN_HEIGHT = 1000
    private const val SCREEN_DENSITY = 1.5f
    private const val FRAME_NANOS = 16_000_000L

    // Args: audio file, report file
    fun run(audio: File, report: File) {
        val result = runCatching { checks(audio) + " " + screen(File(report.path + ".png")) }
        report.writeText(result.fold({ "SELF-TEST OK\n$it" }, { "SELF-TEST FAILED\n${it.stackTraceToString()}" }))
    }

    private fun checks(audio: File): String = runBlocking {
        val dir = createTempDirectory("nextfl-selftest").toFile()
        try {
            withContext(Dispatchers.Main) { }
            val paths = StoragePaths(File(dir, "files"), File(dir, "cache"))
            val repository = ProjectRepository(paths, DesktopPlatform.imageCodec)
            repository.save(SavedProject(doc = Defaults.project(), assets = emptyList(), visual = VisualSettings(title = "Uji")))
            val loaded = repository.load() ?: error("project not reloaded")
            val decoded = DesktopAudioDecoder(AppContainer.SAMPLE_RATE).decode(PickedFile(audio), File(dir, "a.pcm")) {}
            val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            PulseRingTemplate(DesktopPlatform.loadFont("inter_bold.ttf")).draw(Canvas(bitmap), 640, 360, AudioFrame(), 1f / 60, loaded.visual)
            "tracks=${loaded.doc.tracks.size} frames=${decoded.frames} center=${Integer.toHexString(bitmap.getPixel(320, 180))}"
        } finally {
            dir.deleteRecursively()
        }
    }

    // Full UI composed without window
    private fun screen(output: File): String = runBlocking {
        val container = AppContainer(DesktopPlatform())
        container.session.start()
        withTimeout(READY_TIMEOUT_MS) { container.session.ready.first { it } }
        val vm = EditorViewModel(container)
        withContext(Dispatchers.Main) {
            val scene = ImageComposeScene(SCREEN_WIDTH, SCREEN_HEIGHT, Density(SCREEN_DENSITY)) { AppTheme { AppRoot(vm) } }
            try {
                scene.render(0)
                val image = scene.render(FRAME_NANOS)
                output.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            } finally {
                scene.close()
            }
        }
        "screen=${output.name}"
    }
}
