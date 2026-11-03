package id.nextfl.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.engine.MixEngine
import id.nextfl.core.engine.RenderGraphBuilder
import id.nextfl.core.state.ProjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Transport, graph and focus. */
class PlaybackController(
    private val context: Context,
    private val store: ProjectStore,
    private val library: AssetLibrary,
    private val scope: CoroutineScope,
    sampleRate: Int,
) {
    val engine = MixEngine(sampleRate)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(PlaybackAttributes.music)
        .setOnAudioFocusChangeListener { change -> if (change < 0) pause() }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = pause()
    }
    private var session: PlaybackSession? = null
    private var pausedFrame = 0L
    private var endWatch: Job? = null
    private val mutablePlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    init {
        observeProject()
    }

    @OptIn(FlowPreview::class)
    private fun observeProject() {
        val project = store.state.map { it.doc to it.assets }.distinctUntilChanged()
        scope.launch {
            combine(project, library.version) { pair, _ -> pair }.collect { (doc, assets) ->
                engine.setGraph(RenderGraphBuilder.build(doc, assets, engine.sampleRate, library))
            }
        }
        scope.launch {
            project.debounce(STRETCH_DEBOUNCE_MS).collect { (doc, assets) -> library.syncStretchJobs(doc, assets) }
        }
    }

    fun play() {
        if (session != null) return
        audioManager.requestAudioFocus(focusRequest)
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        engine.resetOutputClock(pausedFrame)
        session = PlaybackSession(engine).also { it.start() }
        mutablePlaying.value = true
        endWatch = scope.launch { watchEnd() }
    }

    fun pause() {
        val current = session ?: return
        pausedFrame = positionFrames()
        current.finish()
        session = null
        endWatch?.cancel()
        runCatching { context.unregisterReceiver(noisyReceiver) }
        audioManager.abandonAudioFocusRequest(focusRequest)
        mutablePlaying.value = false
    }

    fun toggle() = if (session == null) play() else pause()

    fun stop() {
        pause()
        pausedFrame = 0
    }

    fun seek(seconds: Double) {
        val frame = (seconds.coerceAtLeast(0.0) * engine.sampleRate).roundToLong()
        if (session != null) engine.seek(frame) else pausedFrame = frame
    }

    fun positionSeconds(): Double = positionFrames().toDouble() / engine.sampleRate

    private fun positionFrames(): Long {
        val current = session ?: return pausedFrame
        return engine.songFrameAtOutput(current.audibleOutputFrame())
    }

    fun audibleOutputFrame(): Long = session?.audibleOutputFrame() ?: 0L

    private suspend fun watchEnd() {
        while (true) {
            delay(END_WATCH_MS)
            val graph = engine.currentGraph
            if (graph.endFrame > 0 && positionFrames() >= graph.endFrame + graph.tailFrames) {
                stop()
                return
            }
        }
    }

    private companion object {
        const val END_WATCH_MS = 150L
        const val STRETCH_DEBOUNCE_MS = 300L
    }
}

/** Spectrum of audible audio. */
class VisualAnalysis(private val playback: PlaybackController) {
    private val analyzer = SpectrumAnalyzer(playback.engine.sampleRate)
    private val window = FloatArray(analyzer.fftSize)

    fun next(dt: Float, sensitivity: Float): AudioFrame {
        if (playback.isPlaying.value) playback.engine.tap.read(playback.audibleOutputFrame(), window) else window.fill(0f)
        return analyzer.analyze(window, dt, sensitivity)
    }
}
