package id.nextfl.core.state

import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.ProjectDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditorState(
    val history: History<ProjectDoc>,
    val assets: AssetMap = emptyMap(),
    val selectedTrackId: String? = null,
    val selectedClipId: String? = null,
) {
    val doc: ProjectDoc get() = history.present
}

/** Single writer for project. */
class ProjectStore(
    initial: ProjectDoc = Defaults.project(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(fresh(initial, emptyMap()))
    val state: StateFlow<EditorState> = mutableState.asStateFlow()
    val current: EditorState get() = mutableState.value

    private var lastCoalesceKey: String? = null
    private var lastCoalesceAt = 0L
    private var gestureStart: ProjectDoc? = null

    fun change(coalesce: String? = null, recipe: (ProjectDoc) -> ProjectDoc) {
        val before = current
        val next = recipe(before.doc)
        if (next == before.doc) return
        val history = if (shouldRecord(coalesce)) before.history.record(next) else before.history.replace(next)
        mutableState.value = pruned(before.copy(history = history))
    }

    fun beginGesture() {
        gestureStart = current.doc
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        val state = current
        if (start == state.doc) return
        mutableState.value = state.copy(history = state.history.copy(present = start).record(state.doc))
    }

    fun undo() = mutableState.update { pruned(it.copy(history = it.history.undo())) }

    fun redo() = mutableState.update { pruned(it.copy(history = it.history.redo())) }

    fun selectTrack(trackId: String?) = mutableState.update { it.copy(selectedTrackId = trackId) }

    fun selectClip(clipId: String?) = mutableState.update { state ->
        val clip = state.doc.clips.find { it.id == clipId }
        state.copy(selectedClipId = clipId, selectedTrackId = clip?.trackId ?: state.selectedTrackId)
    }

    fun addAsset(asset: AudioAsset) = mutableState.update { it.copy(assets = it.assets + (asset.id to asset)) }

    fun updateAsset(assetId: String, transform: (AudioAsset) -> AudioAsset) = mutableState.update { state ->
        val asset = state.assets[assetId] ?: return@update state
        state.copy(assets = state.assets + (assetId to transform(asset)))
    }

    fun removeAsset(assetId: String) = mutableState.update { it.copy(assets = it.assets - assetId) }

    fun load(doc: ProjectDoc, assets: AssetMap) {
        mutableState.value = fresh(doc, assets)
    }

    fun reset() = load(Defaults.project(), emptyMap())

    private fun shouldRecord(coalesce: String?): Boolean {
        if (gestureStart != null) return false
        if (coalesce == null) return true
        val now = clock()
        val merge = coalesce == lastCoalesceKey && now - lastCoalesceAt < COALESCE_WINDOW_MS
        lastCoalesceKey = coalesce
        lastCoalesceAt = now
        return !merge
    }

    private fun pruned(state: EditorState): EditorState {
        val doc = state.doc
        val clipGone = state.selectedClipId != null && doc.clips.none { it.id == state.selectedClipId }
        val trackGone = state.selectedTrackId != null && doc.tracks.none { it.id == state.selectedTrackId }
        if (!clipGone && !trackGone) return state
        return state.copy(
            selectedClipId = if (clipGone) null else state.selectedClipId,
            selectedTrackId = if (trackGone) doc.tracks.firstOrNull()?.id else state.selectedTrackId,
        )
    }

    private fun fresh(doc: ProjectDoc, assets: AssetMap) =
        EditorState(History(present = doc), assets, selectedTrackId = doc.tracks.firstOrNull()?.id)

    companion object {
        const val COALESCE_WINDOW_MS = 1200L
    }
}
