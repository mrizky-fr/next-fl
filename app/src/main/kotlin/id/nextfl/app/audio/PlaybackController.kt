package id.nextfl.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import id.nextfl.core.analysis.AudioFrame
import id.nextfl.core.analysis.SpectrumAnalyzer
import id.nextfl.core.engine.MixEngine
import id.nextfl.core.engine.RenderGraphBuilder
import id.nextfl.core.state.ProjectStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/** Transport, graph and focus. */
class PlaybackController(
    private val context: Context,
    private val store: ProjectStore,
    private val library: AssetLibrary,
    private val scope: CoroutineScope,
    sampleRate: Int,
    private val onError: (String) -> Unit = {},
) {
    val engine = MixEngine(sampleRate)
    private val tuning = OutputTuning()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(PlaybackAttributes.music)
        .setOnAudioFocusChangeListener { change -> if (change < 0) pause() }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = pause()
    }
    private var session: PlaybackSession? = null
    private var lastRestartNanos = 0L
    private var pausedFrame = 0L
    private val mutablePlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    init {
        engine.looping = true
        observeProject()
    }

    // Builds off main thread, latest wins
    private fun observeProject() {
        val project = store.state.map { it.doc to it.assets }.distinctUntilChanged()
        scope.launch(Dispatchers.Default) {
            combine(project, library.version) { pair, _ -> pair }.conflate().collect { (doc, assets) ->
                try {
                    engine.setGraph(RenderGraphBuilder.build(doc, assets, engine.sampleRate, library))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Graph build failed", error)
                }
            }
        }
    }

    fun play() {
        if (session != null) return
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            onError("Audio sedang dipakai aplikasi lain")
            return
        }
        val started = try {
            engine.resetOutputClock(pausedFrame)
            PlaybackSession(engine, tuning, ::onSessionFailed).also { it.start() }
        } catch (error: Exception) {
            Log.e(TAG, "Playback start failed", error)
            audioManager.abandonAudioFocusRequest(focusRequest)
            onError("Output audio tidak tersedia")
            return
        }
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        session = started
        mutablePlaying.value = true
    }

    // Restart once, else stop cleanly
    private fun onSessionFailed(error: Exception) {
        Log.e(TAG, "Audio thread failed", error)
        val failed = Thread.currentThread()
        scope.launch(Dispatchers.Main) {
            if (session == null || session !== failed) return@launch
            pause()
            val now = System.nanoTime()
            if (now - lastRestartNanos > RESTART_COOLDOWN_NANOS) {
                lastRestartNanos = now
                play()
            } else {
                onError("Pemutaran berhenti karena kesalahan audio")
            }
        }
    }

    fun pause() {
        val current = session ?: return
        pausedFrame = positionFrames()
        current.finish()
        session = null
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

    /** Keeps frames rendered before audible. */
    fun requestRenderAhead(frames: Int) = tuning.requestAhead(frames)

    fun audibleTrackPeak(trackId: String): Float = if (isPlaying.value) engine.trackPeakAt(trackId, audibleOutputFrame()) else 0f

    fun audibleMasterPeak(): Float = if (isPlaying.value) engine.masterPeakAt(audibleOutputFrame()) else 0f

    private var shownSeconds = 0.0
    private var shownAtNanos = 0L

    /** Jitter-free position, one per frame. */
    fun displayPosition(frameNanos: Long): Double {
        val raw = positionSeconds()
        val elapsed = (frameNanos - shownAtNanos) / NANOS_PER_SECOND
        val predicted = shownSeconds + elapsed
        shownAtNanos = frameNanos
        // Snap on seek, loop, or resume
        val snap = session == null || elapsed !in 0.0..MAX_FRAME_GAP_SECONDS || abs(raw - predicted) > SNAP_SECONDS
        shownSeconds = if (snap) raw else predicted + (raw - predicted) * CORRECTION
        return shownSeconds
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val NANOS_PER_SECOND = 1e9
        const val MAX_FRAME_GAP_SECONDS = 0.25
        const val SNAP_SECONDS = 0.1
        const val CORRECTION = 0.1
        const val RESTART_COOLDOWN_NANOS = 2_000_000_000L
    }
}

/** Spectrum of audible audio. */
class VisualAnalysis(private val playback: PlaybackController) {
    private val analyzer = SpectrumAnalyzer(playback.engine.sampleRate)
    private val window = FloatArray(analyzer.fftSize)

    fun next(dt: Float, sensitivity: Float, syncMs: Int): AudioFrame {
        val lookahead = lookaheadFrames(syncMs)
        // Buffer grows so sync holds
        playback.requestRenderAhead(lookahead.toInt() + PlaybackSession.BLOCK_FRAMES)
        if (playback.isPlaying.value) playback.engine.tap.read(lookaheadEnd(lookahead), window) else window.fill(0f)
        return analyzer.analyze(window, dt, sensitivity)
    }

    private fun lookaheadFrames(syncMs: Int): Long = syncMs.coerceAtLeast(0).toLong() * playback.engine.sampleRate / MILLIS_PER_SECOND

    // Offsets FFT, display, device latency
    private fun lookaheadEnd(lookahead: Long): Long = minOf(playback.audibleOutputFrame() + lookahead, playback.engine.tap.written)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
