package id.nextfl.desktop.video

import android.graphics.Typeface
import android.graphics.fromBytes
import id.nextfl.core.model.VisualSettings
import id.nextfl.core.pcm.ArrayPcmSource
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Media Foundation encode. */
class DesktopVideoRendererTest {
    private val typeface = Typeface.fromBytes(javaClass.classLoader.getResourceAsStream("inter_bold.ttf")!!.readBytes())
    private val out = File("build/video").apply { mkdirs() }
    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    @Test
    fun rendersMp4WithVideoAndAudio() {
        if (!isWindows) return
        val file = File(out, "render.mp4.part").apply { delete() }
        val progress = mutableListOf<Float>()
        val done = renderer().render(file, onProgress = { progress += it }, isCancelled = { false })
        assertTrue(done)
        assertTrue(topLevelBoxes(file).containsAll(listOf("ftyp", "mdat", "moov")), "boxes: ${topLevelBoxes(file)}")
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue("avc1" in bytes, "no H.264 track")
        assertTrue("mp4a" in bytes, "no AAC track")
        assertTrue(progress.last() == 1f, "progress did not finish")
    }

    @Test
    fun cancelStopsWithoutFinishing() {
        if (!isWindows) return
        val file = File(out, "cancel.mp4.part").apply { delete() }
        assertFalse(renderer().render(file, onProgress = {}, isCancelled = { true }))
    }

    private fun renderer(): DesktopVideoRenderer {
        val rate = 44_100
        val samples = FloatArray(rate * 2) { (sin(2 * PI * 110 * it / rate) * 0.5).toFloat() }
        val settings = VisualSettings(title = "Uji Video", beatZoom = true, keyframePan = true)
        return DesktopVideoRenderer(640, 360, ArrayPcmSource(samples, samples, rate), settings, emptyList(), emptyList(), typeface)
    }

    // Size plus type, 64-bit aware
    private fun topLevelBoxes(file: File): List<String> = RandomAccessFile(file, "r").use { input ->
        val types = mutableListOf<String>()
        var position = 0L
        while (position + 8 <= input.length()) {
            input.seek(position)
            var size = input.readInt().toLong() and 0xFFFF_FFFFL
            val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            if (size == 1L) size = input.readLong()
            if (size < 8) break
            types += type
            position += size
        }
        types
    }
}
