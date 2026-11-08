package id.nextfl.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import id.nextfl.core.engine.MixEngine

/** Focus and headphone-unplug pause. */
class AndroidAudioDevice(private val context: Context) : AudioDevice {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var onInterrupted: () -> Unit = {}
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(PlaybackAttributes.music)
        .setOnAudioFocusChangeListener { change -> if (change < 0) onInterrupted() }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onInterrupted()
    }
    private var registered = false

    override fun acquire(onInterrupted: () -> Unit): Boolean {
        this.onInterrupted = onInterrupted
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return false
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        return true
    }

    override fun release() {
        if (registered) runCatching { context.unregisterReceiver(noisyReceiver) }
        registered = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun open(engine: MixEngine, tuning: OutputTuning, onFailure: (AudioStream, Exception) -> Unit): AudioStream =
        PlaybackSession(engine, tuning, onFailure)
}
