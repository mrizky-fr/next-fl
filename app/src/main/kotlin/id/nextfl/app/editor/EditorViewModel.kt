package id.nextfl.app.editor

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import id.nextfl.app.video.VideoRenderer
import id.nextfl.app.video.VideoPlan
import id.nextfl.core.pcm.PcmFileWriter
import id.nextfl.core.model.AspectRatio
import id.nextfl.core.engine.RenderGraph
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
import id.nextfl.core.model.FxChain
import id.nextfl.core.model.ProjectDoc
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
import id.nextfl.app.data.ExportTarget
import id.nextfl.app.data.MediaExporter
import id.nextfl.app.data.VisualImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    private var exportJob: Job? = null
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

    fun displayPosition(frameNanos: Long): Double = app.playback.displayPosition(frameNanos)

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

    // Mixer FX; null key = own undo step
    fun updateFx(target: FxTarget, key: String?, transform: (FxChain) -> FxChain) = when (target) {
        FxTarget.Master -> store.change(key?.let { "mfx:$it" }) { it.copy(masterFx = transform(it.masterFx)) }
        is FxTarget.Channel -> updateTrack(target.trackId, key?.let { "fx:${target.trackId}:$it" }) { it.withFx(transform(it.fx)) }
    }

    var fxClipboard by mutableStateOf<Any?>(null)
        private set

    fun copyFx(settings: Any) {
        fxClipboard = settings
    }

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

    /** Level heard now, per track. */
    fun trackLevel(trackId: String): Float = app.playback.audibleTrackPeak(trackId)

    fun masterLevel(): Float = app.playback.audibleMasterPeak()

    // Export
    fun exportWav() = launchExport { graph, track ->
        val output = track(app.exporter.createAudio("nextfl-mix-${timestamp()}.wav", MediaExporter.WAV_MIME))
        val total = OfflineRenderer.totalFrames(graph)
        withContext(Dispatchers.Default) {
            WavWriter(app.exporter.openStream(output), AppContainer.SAMPLE_RATE, total).use { writer ->
                OfflineRenderer.render(graph, AppContainer.SAMPLE_RATE, writer, onProgress = { exportBusy("Merender mixdown", it) }, isCancelled = { !isActive })
            }
        }
        output
    }

    fun exportVideo(aspect: AspectRatio) = launchExport { graph, track ->
        val mix = app.paths.renderMix
        try {
            withContext(Dispatchers.Default) {
                PcmFileWriter(mix).use { writer ->
                    OfflineRenderer.render(graph, AppContainer.SAMPLE_RATE, writer, onProgress = { exportBusy("Merender audio", it * AUDIO_SHARE) }, isCancelled = { !isActive })
                }
            }
            ensureActive()
            val plan = withContext(Dispatchers.Default) { VideoPlan.resolve(aspect) }
            val output = track(app.exporter.createVideo("nextfl-video-${timestamp()}.mp4"))
            val settings = visualSettings.value
            val images = background.value to logo.value
            // GL needs one fixed thread
            Executors.newSingleThreadExecutor { Thread(it, "video-render") }.asCoroutineDispatcher().use { renderThread ->
                withContext(renderThread) {
                    MappedPcmSource(mix, AppContainer.SAMPLE_RATE).use { audio ->
                        app.exporter.openDescriptor(output).use { descriptor ->
                            VideoRenderer(plan, audio, settings, images.first, images.second, app.visualTypeface).render(
                                descriptor.fileDescriptor,
                                onProgress = { exportBusy("Merender video", AUDIO_SHARE + it * (1 - AUDIO_SHARE)) },
                                isCancelled = { !isActive },
                            )
                        }
                    }
                }
            }
            output
        } finally {
            mix.delete()
        }
    }

    // Shared guard, publish and cleanup
    private fun launchExport(render: suspend CoroutineScope.(RenderGraph, (ExportTarget) -> ExportTarget) -> ExportTarget) {
        if (exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            val state = store.current
            if (state.doc.clips.isEmpty()) {
                app.messages.toast("Proyek masih kosong, tambahkan audio dulu", ToastTone.ERROR)
                return@launch
            }
            var target: ExportTarget? = null
            exportBusy("Menyiapkan export", 0f)
            try {
                val resolver = prepareExportSources(state.doc, state.assets)
                val graph = RenderGraphBuilder.build(state.doc, state.assets, AppContainer.SAMPLE_RATE, resolver)
                val output = render(graph) { created -> created.also { target = it } }
                ensureActive()
                app.exporter.publish(output)
                val share = ToastAction("Bagikan") { app.share(output.uri, output.mimeType) }
                app.messages.toast("Tersimpan di ${output.location}", ToastTone.SUCCESS, share)
            } catch (cancelled: CancellationException) {
                target?.let(app.exporter::discard)
                app.messages.toast("Export dibatalkan")
                throw cancelled
            } catch (error: Exception) {
                target?.let(app.exporter::discard)
                android.util.Log.e(TAG, "Export failed", error)
                app.messages.toast("Export gagal: ${error.message ?: "tidak diketahui"}", ToastTone.ERROR)
            } finally {
                app.messages.idle()
            }
        }
    }

    private fun exportBusy(message: String, progress: Float?) = app.messages.busy(message, progress, ::cancelExport)

    fun cancelExport() {
        exportJob?.cancel()
    }

    private suspend fun prepareExportSources(doc: ProjectDoc, assets: Map<String, AudioAsset>): SourceResolver {
        for (clip in doc.clips) {
            val rate = clipSpan(clip, assets, doc.bpm).rate
            if (clip.keepPitch && !TimeStretcher.isUnity(rate)) {
                exportBusy("Time-stretch ${clip.name}", null)
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
        private const val AUDIO_SHARE = 0.1f
        private const val NORMALIZE_HEADROOM_DB = 0.3f
        private const val MAX_CLIP_GAIN_DB = 24f
        private const val DECODE_SHARE = 0.85f
        private const val MAX_NAME = 80

        fun formatBpm(bpm: Double): String =
            if (bpm % 1.0 == 0.0) bpm.toInt().toString() else String.format(Locale.ROOT, "%.1f", bpm)
    }
}
