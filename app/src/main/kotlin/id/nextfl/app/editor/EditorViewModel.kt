package id.nextfl.app.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.nextfl.core.dsp.BpmDetector
import id.nextfl.core.dsp.TimeStretcher
import id.nextfl.core.edit.ClipOps
import id.nextfl.core.edit.TrackOps
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.engine.OfflineRenderer
import id.nextfl.core.engine.RenderGraphBuilder
import id.nextfl.core.engine.SourceResolver
import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Clip
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.EqSettings
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.model.ReverbSettings
import id.nextfl.core.model.Track
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.MappedPcmSource
import id.nextfl.core.pcm.WavWriter
import id.nextfl.core.util.AudioMath
import id.nextfl.core.util.Beats
import id.nextfl.core.util.Ids
import id.nextfl.app.AppContainer
import id.nextfl.app.audio.AudioDecodeException
import id.nextfl.app.audio.LoadedAsset
import id.nextfl.app.data.MediaExporter
import id.nextfl.app.data.VisualImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/** Entry point for user actions. */
class EditorViewModel(private val app: AppContainer) : ViewModel() {
    private val store = app.store
    val editor = store.state
    val isPlaying = app.playback.isPlaying
    val visualSettings: StateFlow<VisualSettings> = app.visuals.settings
    val background: StateFlow<android.graphics.Bitmap?> = app.visuals.background
    val logo: StateFlow<android.graphics.Bitmap?> = app.visuals.logo
    val toasts = app.messages.toasts
    val busy = app.messages.busy
    val ready = app.session.ready
    val pendingStretches = app.library.pendingStretches
    val libraryVersion = app.library.version
    val library = app.library
    val analysis = app.analysis
    val engine = app.playback.engine
    val viewport = TimelineViewport()

    private val mutableUi = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = mutableUi.asStateFlow()

    // Transport
    fun togglePlay() {
        if (!isPlaying.value && store.current.doc.clips.isEmpty()) {
            app.messages.toast("Belum ada lagu. Ketuk \"+ Audio\" untuk import dulu.")
            return
        }
        app.playback.toggle()
    }
    fun stop() = app.playback.stop()
    fun seek(seconds: Double) = app.playback.seek(seconds)
    fun position(): Double = app.playback.positionSeconds()

    // View state
    fun updateUi(transform: (UiState) -> UiState) = mutableUi.update(transform)
    fun dismissToast(id: Long) = app.messages.dismiss(id)

    // History
    fun undo() = store.undo()
    fun redo() = store.redo()
    fun beginGesture() = store.beginGesture()
    fun endGesture() = store.endGesture()
    fun change(coalesce: String? = null, recipe: (ProjectDoc) -> ProjectDoc) = store.change(coalesce, recipe)

    // Tracks
    fun selectTrack(trackId: String?) = store.selectTrack(trackId)
    fun selectClip(clipId: String?) = store.selectClip(clipId)

    fun addTrack() {
        store.change { TrackOps.add(it) }
        store.selectTrack(store.current.doc.tracks.lastOrNull()?.id)
    }

    fun removeTrack(trackId: String) = store.change { TrackOps.remove(it, trackId) }

    fun updateTrack(trackId: String, coalesce: String? = null, transform: (Track) -> Track) =
        store.change(coalesce) { TrackOps.update(it, trackId, transform) }

    fun setEq(trackId: String, field: String, transform: (EqSettings) -> EqSettings) =
        updateTrack(trackId, "eq:$trackId:$field") { it.copy(eq = transform(it.eq)) }

    fun setReverb(trackId: String, field: String, transform: (ReverbSettings) -> ReverbSettings) =
        updateTrack(trackId, "rev:$trackId:$field") { it.copy(reverb = transform(it.reverb)) }

    fun setBpm(bpm: Double) = store.change("bpm") {
        it.copy(bpm = (Math.round(bpm.coerceIn(ProjectDoc.MIN_BPM, ProjectDoc.MAX_BPM) * 100) / 100.0))
    }

    fun setMasterDb(db: Float) = store.change("master") { it.copy(masterDb = db) }

    // Clips
    fun updateClip(clipId: String, coalesce: String? = null, transform: (Clip) -> Clip) =
        store.change(coalesce) { ClipOps.update(it, clipId, transform) }

    fun removeClip(clipId: String) = store.change { ClipOps.remove(it, clipId) }

    fun splitClipAtPlayhead(clipId: String) {
        val before = store.current.doc
        store.change { ClipOps.split(it, store.current.assets, clipId, position(), Ids.create("clp")) }
        if (store.current.doc == before) app.messages.toast("Geser playhead ke dalam klip untuk memotong")
    }

    fun duplicateClip(clipId: String) {
        val newId = Ids.create("clp")
        store.change { ClipOps.duplicate(it, store.current.assets, clipId, newId) }
        store.selectClip(newId)
    }

    fun normalizeClip(clipId: String) {
        val clip = store.current.doc.clips.find { it.id == clipId } ?: return
        val peaks = app.library.peaks(clip.assetId) ?: return
        val peak = peaks.peakOfRange(clip.offset, clip.offset + clip.length)
        if (peak <= 0f) return
        val gainDb = (-AudioMath.gainToDb(peak) - NORMALIZE_HEADROOM_DB).coerceIn(AudioMath.MIN_DB, MAX_CLIP_GAIN_DB)
        updateClip(clipId) { it.copy(gainDb = Math.round(gainDb * 10) / 10f) }
    }

    fun setAssetBpm(assetId: String, bpm: Double?) = store.updateAsset(assetId) { it.copy(bpm = bpm?.coerceIn(40.0, 300.0)) }

    // Import
    fun importAudio(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) importOne(context, uri)
            app.messages.idle()
        }
    }

    private suspend fun importOne(context: Context, uri: Uri) {
        val name = displayName(context, uri)
        val assetId = Ids.create("ast")
        try {
            app.messages.busy("Memproses $name", 0f)
            val decoded = app.decoder.decode(uri, app.paths.asset(assetId)) { progress ->
                app.messages.busy("Memproses $name", progress * DECODE_SHARE)
            }
            app.messages.busy("Mendeteksi tempo $name", DECODE_SHARE)
            val loaded = withContext(Dispatchers.Default) {
                val source = MappedPcmSource(app.paths.asset(assetId), AppContainer.SAMPLE_RATE)
                LoadedAsset(source, decoded.peaks) to BpmDetector.detect(source)
            }
            app.library.register(assetId, loaded.first)
            val asset = AudioAsset(assetId, name, loaded.first.source.durationSeconds, loaded.second)
            store.addAsset(asset)
            placeAsset(assetId)
            app.messages.toast(asset.bpm?.let { "$name · ${formatBpm(it)} BPM terdeteksi" } ?: "$name ditambahkan", ToastTone.SUCCESS)
        } catch (error: AudioDecodeException) {
            app.messages.toast("$name: ${error.message}", ToastTone.ERROR)
        }
    }

    fun placeAsset(assetId: String) {
        val state = store.current
        val asset = state.assets[assetId] ?: return
        val clipId = Ids.create("clp")
        val playhead = position()
        store.change { current ->
            val adopted = asset.bpm?.takeIf { current.clips.isEmpty() }?.let { current.copy(bpm = it) } ?: current
            addClipAt(adopted, asset, clipId, playhead, state.selectedTrackId)
        }
        store.selectClip(clipId)
    }

    private fun addClipAt(doc: ProjectDoc, asset: AudioAsset, clipId: String, playhead: Double, preferred: String?): ProjectDoc {
        val assets = store.current.assets + (asset.id to asset)
        val startBeat = floor(Beats.fromSeconds(playhead, doc.bpm) / Beats.PER_BAR) * Beats.PER_BAR
        val start = Beats.toSeconds(startBeat, doc.bpm)
        val end = start + asset.duration
        var target = doc
        var trackId = preferred?.takeIf { ClipOps.isTrackFree(doc, assets, it, start, end) }
            ?: ClipOps.findFreeTrackId(doc, assets, start, end)
        if (trackId == null) {
            target = TrackOps.add(doc)
            trackId = target.tracks.last().id
        }
        val clip = Defaults.clip(trackId, asset.id, asset.name, startBeat, asset.duration).copy(id = clipId)
        return ClipOps.add(target, clip)
    }

    fun removeAsset(assetId: String) {
        if (store.current.doc.clips.any { it.assetId == assetId }) {
            app.messages.toast("Audio masih dipakai di playlist. Hapus klipnya dulu.", ToastTone.ERROR)
            return
        }
        store.removeAsset(assetId)
        app.library.remove(assetId)
    }

    // Export
    fun exportWav() {
        viewModelScope.launch {
            val state = store.current
            if (state.doc.clips.isEmpty()) {
                app.messages.toast("Proyek masih kosong, tambahkan audio dulu", ToastTone.ERROR)
                return@launch
            }
            app.messages.busy("Menyiapkan export", 0f)
            try {
                val resolver = prepareExportSources(state.doc, state.assets)
                val graph = RenderGraphBuilder.build(state.doc, state.assets, AppContainer.SAMPLE_RATE, resolver)
                val total = OfflineRenderer.totalFrames(graph)
                val target = app.exporter.createAudio("nextfl-mix-${timestamp()}.wav", MediaExporter.WAV_MIME)
                val finished = withContext(Dispatchers.Default) {
                    WavWriter(target.stream, AppContainer.SAMPLE_RATE, total).use { writer ->
                        OfflineRenderer.render(graph, AppContainer.SAMPLE_RATE, writer, onProgress = { progress ->
                            app.messages.busy("Merender mixdown", progress)
                        })
                    }
                }
                if (!finished) return@launch app.exporter.discard(target)
                app.exporter.publish(target)
                val share = ToastAction("Bagikan") { app.shareAudio(target.uri) }
                app.messages.toast("Tersimpan di Music/${MediaExporter.FOLDER}", ToastTone.SUCCESS, share)
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Export failed", error)
                app.messages.toast("Export gagal: ${error.message ?: "tidak diketahui"}", ToastTone.ERROR)
            } finally {
                app.messages.idle()
            }
        }
    }

    private suspend fun prepareExportSources(doc: ProjectDoc, assets: Map<String, AudioAsset>): SourceResolver {
        for (clip in doc.clips) {
            val rate = clipSpan(clip, assets, doc.bpm).rate
            if (clip.keepPitch && !TimeStretcher.isUnity(rate)) {
                app.messages.busy("Time-stretch ${clip.name}", null)
                withContext(Dispatchers.Default) { app.library.ensureStretched(clip.assetId, rate) }
            }
        }
        return app.library
    }

    // Project
    fun newProject() {
        app.playback.stop()
        app.library.clear()
        store.reset()
        viewport.scrollSeconds = 0.0
        viewport.scrollY = 0f
        app.session.flush()
        app.messages.toast("Proyek baru siap", ToastTone.SUCCESS)
    }

    // Visual template
    fun updateVisual(transform: (VisualSettings) -> VisualSettings) = app.visuals.settings.update(transform)

    fun setImage(kind: VisualImage, uri: Uri) {
        viewModelScope.launch {
            runCatching { app.repository.importImage(kind, uri) }
                .onSuccess { app.visuals.image(kind).value = it }
                .onFailure { app.messages.toast("Gambar tidak bisa dibaca", ToastTone.ERROR) }
        }
    }

    fun removeImage(kind: VisualImage) {
        app.repository.deleteImage(kind)
        app.visuals.image(kind).value = null
    }

    fun onAppBackgrounded() = app.session.flush()

    private fun displayName(context: Context, uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return (name ?: "Audio").substringBeforeLast('.').take(MAX_NAME)
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())

    companion object {
        private const val TAG = "EditorViewModel"
        private const val NORMALIZE_HEADROOM_DB = 0.3f
        private const val MAX_CLIP_GAIN_DB = 24f
        private const val DECODE_SHARE = 0.85f
        private const val MAX_NAME = 80

        fun formatBpm(bpm: Double): String =
            if (bpm % 1.0 == 0.0) bpm.toInt().toString() else String.format(Locale.ROOT, "%.1f", bpm)
    }
}
