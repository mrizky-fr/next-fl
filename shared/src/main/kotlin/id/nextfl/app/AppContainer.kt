package id.nextfl.app

import android.util.Log
import id.nextfl.app.audio.AssetLibrary
import id.nextfl.app.audio.PlaybackController
import id.nextfl.app.audio.VisualAnalysis
import id.nextfl.app.data.ProjectRepository
import id.nextfl.app.data.StoragePaths
import id.nextfl.app.editor.SessionManager
import id.nextfl.app.editor.ToastTone
import id.nextfl.app.editor.UiMessages
import id.nextfl.app.editor.VisualState
import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.Platform
import id.nextfl.core.state.ProjectStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired application singletons. */
class AppContainer(val platform: Platform) {
    // Logs instead of killing app
    private val crashGuard = CoroutineExceptionHandler { _, error -> Log.e(TAG, "Uncaught coroutine error", error) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    val paths = StoragePaths(platform.filesDir, platform.cacheDir)
    val store = ProjectStore()
    val library = AssetLibrary(paths, SAMPLE_RATE)
    val messages = UiMessages(scope)
    val playback = PlaybackController(platform.audioDevice, store, library, scope, SAMPLE_RATE) { messages.toast(it, ToastTone.ERROR) }
    val analysis = VisualAnalysis(playback)
    val repository = ProjectRepository(paths, platform.images)
    val visuals = VisualState()
    val session = SessionManager(store, library, repository, visuals, scope)

    fun share(target: ExportTarget) {
        if (!platform.share(target)) messages.toast("Tidak ada aplikasi untuk berbagi", ToastTone.ERROR)
    }

    companion object {
        private const val TAG = "AppContainer"
        const val SAMPLE_RATE = 44_100
    }
}
