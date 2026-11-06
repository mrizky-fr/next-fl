package id.nextfl.core.pcm

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmQuantizeTest {
    private fun wavCodes(left: FloatArray): ShortArray {
        val bytes = ByteArrayOutputStream()
        WavWriter(bytes, 44_100, left.size.toLong()).use { it.write(left, left, left.size) }
        val view = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(left.size) { view.getShort(WavWriter.HEADER_BYTES + it * 4) }
    }

    // Decoded assets must survive storage
    @Test
    fun assetStorageIsBitExact() {
        val codes = intArrayOf(30_000, -30_000, 16_385, -1, 32_767, -32_768)
        val values = FloatArray(codes.size) { codes[it] / 32768f }
        val file = File.createTempFile("pcm", ".raw")
        try {
            PcmFileWriter(file).use { it.write(values, values, values.size) }
            MappedPcmSource(file, 44_100).use { source ->
                for (i in codes.indices) assertEquals(values[i], source.left(i))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun nanDoesNotCrashExport() {
        val codes = wavCodes(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f))
        assertEquals(0, codes[0].toInt())
        assertEquals(32_767, codes[1].toInt())
        assertEquals(-32_768, codes[2].toInt())
    }

    @Test
    fun exportDitherStaysWithinOneStep() {
        val codes = wavCodes(FloatArray(2_000) { 0.25f })
        assertTrue(codes.all { it.toInt() in 8_191..8_193 }, "dither too loud")
        assertTrue(codes.toSet().size > 1, "dither missing")
        assertTrue(wavCodes(FloatArray(100)).all { it.toInt() == 0 }, "silence must stay silent")
    }
}
