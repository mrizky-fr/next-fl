package id.nextfl.desktop.audio

import id.nextfl.app.audio.OutputTuning
import id.nextfl.core.engine.MixEngine
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAudioDeviceTest {
    // Empty graph plays silence only
    @Test
    fun clockAdvancesWhilePlaying() {
        if (!AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, null))) return
        val engine = MixEngine(RATE)
        var failure: Exception? = null
        val stream = DesktopAudioDevice().open(engine, OutputTuning()) { _, error -> failure = error }
        stream.begin()
        Thread.sleep(PLAY_MS)
        val heard = stream.audibleOutputFrame()
        stream.finish()
        assertNull(failure)
        assertTrue(heard > RATE * PLAY_MS / 1000 / 3, "clock stuck at $heard")
        assertTrue(stream.audibleOutputFrame() >= heard)
    }

    private companion object {
        const val RATE = 44_100
        const val PLAY_MS = 600L
    }
}
