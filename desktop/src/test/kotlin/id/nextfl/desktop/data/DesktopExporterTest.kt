package id.nextfl.desktop.data

import id.nextfl.app.platform.Exporter
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopExporterTest {
    private val home = createTempDirectory("home").toFile()
    private val exporter = DesktopExporter(home)

    @AfterTest
    fun cleanup() {
        home.deleteRecursively()
    }

    @Test
    fun publishMovesPartIntoMusicFolder() {
        val target = exporter.createAudio("mix.wav", Exporter.WAV_MIME)
        exporter.openStream(target).use { it.write(byteArrayOf(1, 2, 3)) }
        assertFalse(target.file.exists(), "visible before publish")
        exporter.publish(target)
        assertEquals(3L, target.file.length())
        assertEquals("Music", target.file.parentFile.parentFile.name)
        assertFalse(target.part.exists())
    }

    @Test
    fun discardLeavesNothing() {
        val target = exporter.createAudio("mix.mp3", Exporter.MP3_MIME)
        exporter.openStream(target).use { it.write(1) }
        exporter.discard(target)
        assertTrue(target.file.parentFile.listFiles().isNullOrEmpty())
    }

    // Same-minute exports must not overwrite
    @Test
    fun repeatedNamesGetSuffix() {
        val first = exporter.createAudio("mix.wav", Exporter.WAV_MIME)
        exporter.openStream(first).use { it.write(1) }
        exporter.publish(first)
        val second = exporter.createAudio("mix.wav", Exporter.WAV_MIME)
        assertEquals("mix (1).wav", second.file.name)
    }
}
