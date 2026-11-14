package id.nextfl.app.editor

import android.graphics.Bitmap
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.Sanitize
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.state.ProjectStore
import id.nextfl.app.audio.AssetLibrary
import id.nextfl.app.audio.LoadedAsset
import id.nextfl.app.data.CollabImage
import id.nextfl.app.data.ProjectRepository
import id.nextfl.app.data.SavedProject
import id.nextfl.app.data.VisualImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VisualState {
    val settings = MutableStateFlow(VisualSettings())
    val background = MutableStateFlow<Bitmap?>(null)
    val logo = MutableStateFlow<Bitmap?>(null)
    val collabBackgrounds = MutableStateFlow<List<CollabImage>>(emptyList())
    val collabLogos = MutableStateFlow<List<CollabImage>>(emptyList())

    fun image(kind: VisualImage) = if (kind == VisualImage.BACKGROUND) background else logo

    fun collab(kind: VisualImage) = if (kind == VisualImage.BACKGROUND) collabBackgrounds else collabLogos
}

/** Restores project, then autosaves. */
class SessionManager(
    private val store: ProjectStore,
    private val library: AssetLibrary,
    private val repository: ProjectRepository,
    private val visuals: VisualState,
    private val scope: CoroutineScope,
) {
    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

    fun start() {
        scope.launch {
            runCatching { restore() }.onFailure { android.util.Log.e(TAG, "Restore failed", it) }
            mutableReady.value = true
            autosave()
        }
    }

    fun flush() {
        if (mutableReady.value) scope.launch { saveNow() }
    }

    suspend fun saveNow() {
        val state = store.current
        val snapshot = SavedProject(doc = state.doc, assets = state.assets.values.toList(), visual = visuals.settings.value)
        runCatching { repository.save(snapshot) }.onFailure { android.util.Log.e(TAG, "Autosave failed", it) }
    }

    private suspend fun restore() {
        VisualImage.entries.forEach { kind ->
            visuals.image(kind).value = repository.loadImage(kind)
            visuals.collab(kind).value = repository.loadCollab(kind)
        }
        val saved = repository.load() ?: return
        visuals.settings.value = Sanitize.visual(saved.visual)
        val assets: AssetMap = withContext(Dispatchers.Default) {
            saved.assets.mapNotNull { asset ->
                openSafely(asset.id)?.let { loaded ->
                    library.register(asset.id, loaded)
                    asset.id to asset
                }
            }.toMap()
        }
        store.load(Sanitize.project(saved.doc, assets), assets)
    }

    // One bad file keeps project
    private fun openSafely(assetId: String): LoadedAsset? = try {
        library.open(assetId)
    } catch (error: Exception) {
        android.util.Log.w(TAG, "Asset unreadable: $assetId", error)
        null
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave() {
        val project = store.state.map { it.doc to it.assets }.distinctUntilChanged()
        combine(project, visuals.settings) { _, _ -> }
            .drop(1)
            .debounce(AUTOSAVE_DEBOUNCE_MS)
            .collect { saveNow() }
    }

    private companion object {
        const val TAG = "SessionManager"
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
