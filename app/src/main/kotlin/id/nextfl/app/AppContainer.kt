package id.nextfl.app

import android.app.Application
import android.graphics.Typeface
import android.net.Uri
import id.nextfl.core.state.ProjectStore
import id.nextfl.app.audio.AssetLibrary
import id.nextfl.app.audio.AudioDecoder
import id.nextfl.app.audio.PlaybackController
import id.nextfl.app.audio.VisualAnalysis
import id.nextfl.app.data.MediaExporter
import id.nextfl.app.data.ProjectRepository
import id.nextfl.app.data.StoragePaths
import id.nextfl.app.editor.SessionManager
import id.nextfl.app.editor.UiMessages
import id.nextfl.app.editor.VisualState
import id.nextfl.app.ui.theme.Fonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired application singletons. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val paths = StoragePaths(app.filesDir, app.cacheDir)
    val store = ProjectStore()
    val library = AssetLibrary(paths, SAMPLE_RATE)
    val playback = PlaybackController(app, store, library, scope, SAMPLE_RATE)
    val analysis = VisualAnalysis(playback)
    val decoder = AudioDecoder(app, SAMPLE_RATE)
    val repository = ProjectRepository(paths, app.contentResolver)
    val exporter = MediaExporter(app.contentResolver)
    val visuals = VisualState()
    val messages = UiMessages(scope)
    val session = SessionManager(store, library, repository, visuals, scope)
    val visualTypeface: Typeface by lazy { Fonts.bold(app) }
    fun share(uri: Uri, mimeType: String) = app.startActivity(exporter.shareIntent(uri, mimeType))

    companion object {
        const val SAMPLE_RATE = 44_100
    }
}
