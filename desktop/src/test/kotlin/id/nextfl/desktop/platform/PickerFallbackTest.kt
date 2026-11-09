package id.nextfl.desktop.platform

import id.nextfl.app.platform.withPickerFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PickerFallbackTest {
    private val fallbackFile = File("fallback.mp3")

    @Test
    fun blockedNativeLibraryUsesFallback() = runBlocking {
        val files = withPickerFallback({ throw UnsatisfiedLinkError("jnidispatch blocked") }) { listOf(fallbackFile) }
        assertEquals(listOf(fallbackFile), files)
    }

    @Test
    fun brokenNativeClassUsesFallback() = runBlocking {
        val files = withPickerFallback({ throw NoClassDefFoundError("com/sun/jna/Native") }) { listOf(fallbackFile) }
        assertEquals(listOf(fallbackFile), files)
    }

    @Test
    fun cancelIsNotSwallowed() {
        assertFailsWith<CancellationException> {
            runBlocking { withPickerFallback({ throw CancellationException("closed") }) { listOf(fallbackFile) } }
        }
    }
}
