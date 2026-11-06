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
import id.nextfl.app.editor.ToastTone
import id.nextfl.app.editor.UiMessages
import id.nextfl.app.editor.VisualState
import id.nextfl.app.ui.theme.Fonts
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired application singletons. */
class AppContainer(private val app: Application) {
    // Logs instead of killing app
    private val crashGuard = CoroutineExceptionHandler { _, error -> Log.e(TAG, "Uncaught coroutine error", error) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    val paths = StoragePaths(app.filesDir, app.cacheDir)
    val store = ProjectStore()
    val library = AssetLibrary(paths, SAMPLE_RATE)
    val messages = UiMessages(scope)
    val playback = PlaybackController(app, store, library, scope, SAMPLE_RATE) { messages.toast(it, ToastTone.ERROR) }
    val analysis = VisualAnalysis(playback)
    val decoder = AudioDecoder(app, SAMPLE_RATE)
    val repository = ProjectRepository(paths, app.contentResolver)
    val exporter = MediaExporter(app.contentResolver)
    val visuals = VisualState()
    val session = SessionManager(store, library, repository, visuals, scope)
    val visualTypeface: Typeface by lazy { Fonts.bold(app) }
    fun share(uri: Uri, mimeType: String) {
        try {
            app.startActivity(exporter.shareIntent(uri, mimeType))
        } catch (error: RuntimeException) {
            Log.e(TAG, "Share failed", error)
            messages.toast("Tidak ada aplikasi untuk berbagi", ToastTone.ERROR)
        }
    }

    companion object {
        private const val TAG = "AppContainer"
        const val SAMPLE_RATE = 44_100
    }
}
