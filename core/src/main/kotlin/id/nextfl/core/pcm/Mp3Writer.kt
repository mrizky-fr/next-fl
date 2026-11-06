package id.nextfl.core.pcm

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/** LAME CBR stereo MP3 with gapless tag. */
class Mp3Writer(
    private val output: OutputStream,
    sampleRate: Int,
    bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
    private val scratch: File,
) : StereoSink, Closeable {
    private val lame = Lame()
    private val tag = VBRTag()
    private val flags = configure(lame, tag, sampleRate, bitrateKbps)

    // Tag needs a seekable file
    private val file = RandomAccessFile(scratch, "rw").also { it.setLength(0) }
    private var leftInts = IntArray(0)
    private var rightInts = IntArray(0)
    private var encoded = ByteArray(FLUSH_BYTES)
    private var closed = false

    override fun write(left: FloatArray, right: FloatArray, count: Int) {
        if (count <= 0) return
        ensureCapacity(count)
        for (i in 0 until count) {
            leftInts[i] = toInt32(left[i])
            rightInts[i] = toInt32(right[i])
        }
        val bytes = lame.lame_encode_buffer_int(flags, leftInts, rightInts, count, encoded, 0, encoded.size)
        if (bytes < 0) throw IOException("MP3 encode failed ($bytes)")
        file.write(encoded, 0, bytes)
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            val bytes = lame.lame_encode_flush(flags, encoded, 0, encoded.size)
            if (bytes > 0) file.write(encoded, 0, bytes)
            writeGaplessTag()
            copyToOutput()
        } finally {
            lame.lame_close(flags)
            file.close()
            scratch.delete()
            output.close()
        }
    }

    // Info frame stores delay, padding
    private fun writeGaplessTag() {
        val frame = ByteArray(VBRTag.MAXFRAMESIZE)
        val size = tag.getLameTagFrame(flags, frame)
        if (size <= 0 || size > file.length()) return
        file.seek(0)
        file.write(frame, 0, size)
    }

    private fun copyToOutput() {
        val buffer = ByteArray(COPY_BYTES)
        file.seek(0)
        while (true) {
            val read = file.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
    }

    // LAME worst case: 1.25n + 7200
    private fun ensureCapacity(count: Int) {
        if (leftInts.size < count) {
            leftInts = IntArray(count)
            rightInts = IntArray(count)
        }
        val needed = count + count / 4 + FLUSH_BYTES
        if (encoded.size < needed) encoded = ByteArray(needed)
    }

    companion object {
        const val DEFAULT_BITRATE_KBPS = 320
        val BITRATES_KBPS = listOf(128, 192, 256, 320)
        private const val CHANNELS = 2
        private const val QUALITY = 2
        private const val FLUSH_BYTES = 7200
        private const val COPY_BYTES = 64 * 1024
        private const val FULL_SCALE = 2_147_483_647f

        // NaN becomes silence, no throw
        private fun toInt32(sample: Float): Int = (sample.coerceIn(-1f, 1f) * FULL_SCALE).toInt()

        // Same wiring as jump3r LameEncoder
        private fun configure(lame: Lame, vbrTag: VBRTag, sampleRate: Int, bitrateKbps: Int): LameGlobalFlags {
            val gain = GainAnalysis()
            val bits = BitStream()
            val presets = Presets()
            val quantizePvt = QuantizePVT()
            val quantize = Quantize()
            val version = Version()
            val id3 = ID3Tag()
            val reservoir = Reservoir()
            val takehiro = Takehiro()
            val parse = Parse()
            val mpg = MPGLib()
            val decoderInterface = Interface()
            val common = Common()
            lame.setModules(gain, bits, presets, quantizePvt, quantize, vbrTag, version, id3, mpg)
            bits.setModules(gain, mpg, version, vbrTag)
            id3.setModules(bits, version)
            presets.setModules(lame)
            quantize.setModules(bits, reservoir, quantizePvt, takehiro)
            quantizePvt.setModules(takehiro, reservoir, lame.enc.psy)
            reservoir.setModules(bits)
            takehiro.setModules(quantizePvt)
            vbrTag.setModules(lame, bits, version)
            GetAudio().setModules(parse, mpg)
            parse.setModules(version, id3, presets)
            mpg.setModules(decoderInterface, common)
            decoderInterface.setModules(vbrTag, common)
            val flags = lame.lame_init()
            flags.num_channels = CHANNELS
            flags.in_samplerate = sampleRate
            flags.out_samplerate = sampleRate
            flags.mode = MPEGMode.JOINT_STEREO
            flags.VBR = VbrMode.vbr_off
            flags.brate = bitrateKbps
            flags.quality = QUALITY
            flags.bWriteVbrTag = true
            id3.id3tag_init(flags)
            flags.write_id3tag_automatic = false
            flags.findReplayGain = false
            val status = lame.lame_init_params(flags)
            if (status < 0) throw IOException("MP3 encoder init failed ($status)")
            return flags
        }
    }
}
