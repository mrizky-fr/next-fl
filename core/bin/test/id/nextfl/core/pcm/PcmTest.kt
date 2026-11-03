package id.nextfl.core.pcm

import id.nextfl.core.TestSignals
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmTest {
    @Test
    fun fileRoundTripThroughMappedSource() {
        val file = File.createTempFile("pcm", ".raw")
        try {
            PcmFileWriter(file).use { it.write(floatArrayOf(0f, 0.5f, -1f, 2f), floatArrayOf(0.25f, -0.5f, 1f, -2f), 4) }
            MappedPcmSource(file, 44_100).use { source ->
                assertEquals(4, source.frames)
                assertEquals(0.5f, source.left(1), 1e-4f)
                assertEquals(-0.5f, source.right(1), 1e-4f)
                assertEquals(1f, source.left(3), 1e-3f)
                assertEquals(0f, source.left(99))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun wavHeaderDescribesStereo16Bit() {
        val bytes = ByteArrayOutputStream()
        WavWriter(bytes, 48_000, 10).use { it.write(FloatArray(4), FloatArray(4), 4) }
        val data = bytes.toByteArray()
        val view = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(data, 0, 4))
        assertEquals("WAVE", String(data, 8, 4))
        assertEquals(2, view.getShort(22).toInt())
        assertEquals(48_000, view.getInt(24))
        assertEquals(40, view.getInt(40))
        assertEquals(WavWriter.HEADER_BYTES + 40, data.size)
    }

    @Test
    fun resamplerKeepsFrequencyAndLength() {
        val input = TestSignals.sine(1000.0, 1.0, 48_000)
        val sink = ArrayStereoSink()
        val resampler = StreamingResampler(48_000, 44_100, sink)
        input.toList().chunked(3000).forEach { chunk ->
            val array = chunk.toFloatArray()
            resampler.write(array, array, array.size)
        }
        resampler.finish()
        val output = sink.toSource(44_100)
        assertTrue(kotlin.math.abs(output.frames - 44_100) < 64, "frames ${output.frames}")
        val left = FloatArray(output.frames) { output.left(it) }
        assertEquals(1000.0, TestSignals.zeroCrossingRate(left.copyOfRange(100, 44_000), 44_100), 10.0)
    }

    @Test
    fun peaksFindLoudestRange() {
        val data = FloatArray(44_100)
        data[22_050] = -0.8f
        val peaks = WaveformPeaks.compute(TestSignals.stereo(data, 44_100))
        assertEquals(0.8f, peaks.peakOfRange(0.4, 0.6), 1e-6f)
        assertEquals(0f, peaks.peakOfRange(0.0, 0.3))
    }
}
