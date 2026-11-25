package id.nextfl.desktop.video

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

private val ADDRESS = ValueLayout.ADDRESS
private val INT = ValueLayout.JAVA_INT
private val LONG = ValueLayout.JAVA_LONG
private val NULL: MemorySegment = MemorySegment.NULL

// Shapes include COM this first
private val D_A = FunctionDescriptor.of(INT, ADDRESS)
private val D_AA = FunctionDescriptor.of(INT, ADDRESS, ADDRESS)
private val D_AAA = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS)
private val D_AAAA = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
private val D_AI = FunctionDescriptor.of(INT, ADDRESS, INT)
private val D_AL = FunctionDescriptor.of(INT, ADDRESS, LONG)
private val D_AAI = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, INT)
private val D_AAL = FunctionDescriptor.of(INT, ADDRESS, ADDRESS, LONG)
private val D_AIA = FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS)
private val D_AIAA = FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS, ADDRESS)
private val D_II = FunctionDescriptor.of(INT, INT, INT)
private val D_IA = FunctionDescriptor.of(INT, INT, ADDRESS)
private val D_NONE = FunctionDescriptor.of(INT)
private val D_VOID = FunctionDescriptor.ofVoid()

/** Cached downcalls into Windows DLLs. */
internal object Native {
    private val linker = Linker.nativeLinker()
    private val handles = ConcurrentHashMap<FunctionDescriptor, MethodHandle>()
    private val libraries = ConcurrentHashMap<String, SymbolLookup>()

    // Missing DLL: Windows N edition
    fun library(name: String): SymbolLookup = try {
        libraries.computeIfAbsent(name) { SymbolLookup.libraryLookup(it, Arena.global()) }
    } catch (error: IllegalArgumentException) {
        throw IOException("Media Foundation tidak tersedia di Windows ini", error)
    }

    fun function(library: SymbolLookup, name: String): MemorySegment =
        library.find(name).orElseThrow { IOException("Fungsi $name tidak ditemukan") }

    fun call(target: MemorySegment, descriptor: FunctionDescriptor, vararg args: Any): Int =
        handle(descriptor).invokeWithArguments(target, *args) as Int

    fun callVoid(target: MemorySegment, descriptor: FunctionDescriptor, vararg args: Any) {
        handle(descriptor).invokeWithArguments(target, *args)
    }

    private fun handle(descriptor: FunctionDescriptor): MethodHandle =
        handles.computeIfAbsent(descriptor) { linker.downcallHandle(it) }
}

// COM call by vtable slot
private fun MemorySegment.com(slot: Int, descriptor: FunctionDescriptor, vararg args: Any): Int {
    val width = ADDRESS.byteSize()
    val table = reinterpret(width).get(ADDRESS, 0L).reinterpret(width * (slot + 1))
    return Native.call(table.getAtIndex(ADDRESS, slot.toLong()), descriptor, this, *args)
}

private fun hr(result: Int, step: String) {
    if (result < 0) throw IOException("Media Foundation gagal di $step (0x%08X)".format(result))
}

/** Media Foundation GUID constants. */
private object Guid {
    val MAJOR_TYPE = of("48eba18e-f8c9-4687-bf11-0a74c9f96a8f")
    val SUBTYPE = of("f7e34c9a-42e8-4714-b74b-cb29d72c35e5")
    val AVG_BITRATE = of("20332624-fb0d-4d9e-bd0d-cbf6786c102e")
    val INTERLACE_MODE = of("e2724bb8-e676-4806-b4b2-a8d6efb44ccd")
    val FRAME_SIZE = of("1652c33d-d6b2-4012-b834-72030849a37d")
    val FRAME_RATE = of("c459a2e8-3d2c-4e44-b132-fee5156c7bb0")
    val PIXEL_ASPECT = of("c6376a1e-8d0a-4027-be45-6d9a0ad39bb6")
    val DEFAULT_STRIDE = of("644b4e48-1e02-4516-b0eb-c01ca9d49ac6")
    val VIDEO_PROFILE = of("ad76a80b-2d5c-4e0b-b375-64e520137036")
    val AUDIO_CHANNELS = of("37e48bf5-645e-4c5b-89de-ada9e29b696a")
    val AUDIO_RATE = of("5faeeae7-0290-4c31-9e8a-c534f68d9dba")
    val AUDIO_BITS = of("f2deb57f-40fa-4764-aa33-ed4f2d1ff669")
    val AUDIO_BYTES_PER_SECOND = of("1aab75c8-cfef-451c-ab95-ac034b8e1731")
    val AUDIO_BLOCK_ALIGN = of("322de230-9eeb-43bd-ab7a-ff412251541d")
    val CONTAINER_TYPE = of("150ff23f-4abc-478b-ac4f-e1916fba1cca")
    val CONTAINER_MP4 = of("dc6cd05d-b9d0-40ef-bd35-fa622c1ab28a")
    val VIDEO = of("73646976-0000-0010-8000-00aa00389b71")
    val AUDIO = of("73647561-0000-0010-8000-00aa00389b71")
    val H264 = of("34363248-0000-0010-8000-00aa00389b71")
    val RGB32 = of("00000016-0000-0010-8000-00aa00389b71")
    val PCM = of("00000001-0000-0010-8000-00aa00389b71")
    val AAC = of("00001610-0000-0010-8000-00aa00389b71")

    // Mixed-endian Windows GUID layout
    private fun of(text: String): MemorySegment {
        val hex = text.replace("-", "")
        val guid = Arena.global().allocate(GUID_BYTES, 8L)
        guid.set(ValueLayout.JAVA_INT, 0L, hex.substring(0, 8).toLong(16).toInt())
        guid.set(ValueLayout.JAVA_SHORT, 4L, hex.substring(8, 12).toInt(16).toShort())
        guid.set(ValueLayout.JAVA_SHORT, 6L, hex.substring(12, 16).toInt(16).toShort())
        for (i in 0 until 8) guid.set(ValueLayout.JAVA_BYTE, 8L + i, hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
        return guid
    }

    private const val GUID_BYTES = 16L
}

/** Media Foundation exports used. */
private class MfApi {
    private val ole = Native.library("ole32")
    private val platform = Native.library("mfplat")
    private val readWrite = Native.library("mfreadwrite")
    val coInitializeEx = Native.function(ole, "CoInitializeEx")
    val coUninitialize = Native.function(ole, "CoUninitialize")
    val startup = Native.function(platform, "MFStartup")
    val shutdown = Native.function(platform, "MFShutdown")
    val createAttributes = Native.function(platform, "MFCreateAttributes")
    val createMediaType = Native.function(platform, "MFCreateMediaType")
    val createSample = Native.function(platform, "MFCreateSample")
    val createMemoryBuffer = Native.function(platform, "MFCreateMemoryBuffer")
    val createSinkWriter = Native.function(readWrite, "MFCreateSinkWriterFromURL")
}

/** H.264 plus AAC MP4 writer. */
internal class MfSinkWriter(
    output: File,
    private val width: Int,
    private val height: Int,
    fps: Int,
    videoBitrate: Int,
    sampleRate: Int,
    aacBytesPerSecond: Int,
) : Closeable {
    private val arena = Arena.ofConfined()
    private val pointer = arena.allocate(ADDRESS)
    private val streamIndex = arena.allocate(INT)
    private var api: MfApi? = null
    private var comReady = false
    private var mfReady = false
    private var writer = NULL
    private var videoStream = 0
    private var audioStream = 0
    private var closed = false

    init {
        try {
            val mf = MfApi().also { api = it }
            comReady = Native.call(mf.coInitializeEx, D_AI, NULL, COINIT_MULTITHREADED) >= 0
            hr(Native.call(mf.startup, D_II, MF_VERSION, MFSTARTUP_FULL), "MFStartup")
            mfReady = true
            writer = createWriter(mf, output)
            videoStream = addVideo(mf, fps, videoBitrate)
            audioStream = addAudio(mf, sampleRate, aacBytesPerSecond)
            hr(writer.com(BEGIN_WRITING, D_A), "BeginWriting")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /** Top-down BGRA, 100 ns times. */
    fun writeVideo(pixels: MemorySegment, rowBytes: Int, time: Long, duration: Long) {
        val packed = width * BYTES_PER_PIXEL
        writeSample(videoStream, packed * height, time, duration) { target ->
            if (rowBytes == packed) {
                MemorySegment.copy(pixels, 0L, target, 0L, packed.toLong() * height)
            } else {
                for (row in 0 until height) MemorySegment.copy(pixels, row.toLong() * rowBytes, target, row.toLong() * packed, packed.toLong())
            }
        }
    }

    /** Interleaved 16-bit stereo PCM. */
    fun writeAudio(pcm: ByteArray, length: Int, time: Long, duration: Long) {
        writeSample(audioStream, length, time, duration) { target -> MemorySegment.copy(pcm, 0, target, ValueLayout.JAVA_BYTE, 0L, length) }
    }

    fun finish() = hr(writer.com(FINALIZE, D_A), "Finalize")

    // Unfinished file is left partial
    override fun close() {
        if (closed) return
        closed = true
        release(writer)
        writer = NULL
        api?.let { mf ->
            if (mfReady) Native.call(mf.shutdown, D_NONE)
            if (comReady) Native.callVoid(mf.coUninitialize, D_VOID)
        }
        arena.close()
    }

    private fun createWriter(mf: MfApi, output: File): MemorySegment {
        hr(Native.call(mf.createAttributes, D_AI, pointer, 1), "MFCreateAttributes")
        val attributes = pointer.get(ADDRESS, 0L)
        try {
            // Part suffix hides .mp4
            hr(attributes.com(SET_GUID, D_AAA, Guid.CONTAINER_TYPE, Guid.CONTAINER_MP4), "SetGUID")
            hr(Native.call(mf.createSinkWriter, D_AAAA, wide(output.absolutePath), NULL, attributes, pointer), "MFCreateSinkWriterFromURL")
            return pointer.get(ADDRESS, 0L)
        } finally {
            release(attributes)
        }
    }

    private fun addVideo(mf: MfApi, fps: Int, bitrate: Int): Int {
        val size = pack(width, height)
        val rate = pack(fps, 1)
        val square = pack(1, 1)
        val stream = withMediaType(mf) { encoded ->
            encoded.setGuid(Guid.MAJOR_TYPE, Guid.VIDEO)
            encoded.setGuid(Guid.SUBTYPE, Guid.H264)
            encoded.setInt(Guid.AVG_BITRATE, bitrate)
            encoded.setInt(Guid.INTERLACE_MODE, PROGRESSIVE)
            encoded.setLong(Guid.FRAME_SIZE, size)
            encoded.setLong(Guid.FRAME_RATE, rate)
            encoded.setLong(Guid.PIXEL_ASPECT, square)
            encoded.setInt(Guid.VIDEO_PROFILE, H264_HIGH)
            addStream(encoded)
        }
        withMediaType(mf) { raw ->
            raw.setGuid(Guid.MAJOR_TYPE, Guid.VIDEO)
            raw.setGuid(Guid.SUBTYPE, Guid.RGB32)
            raw.setInt(Guid.INTERLACE_MODE, PROGRESSIVE)
            raw.setLong(Guid.FRAME_SIZE, size)
            raw.setLong(Guid.FRAME_RATE, rate)
            raw.setLong(Guid.PIXEL_ASPECT, square)
            // Positive stride means top-down
            raw.setInt(Guid.DEFAULT_STRIDE, width * BYTES_PER_PIXEL)
            setInput(stream, raw)
        }
        return stream
    }

    private fun addAudio(mf: MfApi, sampleRate: Int, aacBytesPerSecond: Int): Int {
        val stream = withMediaType(mf) { aac ->
            aac.setGuid(Guid.MAJOR_TYPE, Guid.AUDIO)
            aac.setGuid(Guid.SUBTYPE, Guid.AAC)
            aac.setInt(Guid.AUDIO_BITS, PCM_BITS)
            aac.setInt(Guid.AUDIO_RATE, sampleRate)
            aac.setInt(Guid.AUDIO_CHANNELS, CHANNELS)
            aac.setInt(Guid.AUDIO_BYTES_PER_SECOND, aacBytesPerSecond)
            addStream(aac)
        }
        withMediaType(mf) { pcm ->
            pcm.setGuid(Guid.MAJOR_TYPE, Guid.AUDIO)
            pcm.setGuid(Guid.SUBTYPE, Guid.PCM)
            pcm.setInt(Guid.AUDIO_BITS, PCM_BITS)
            pcm.setInt(Guid.AUDIO_RATE, sampleRate)
            pcm.setInt(Guid.AUDIO_CHANNELS, CHANNELS)
            pcm.setInt(Guid.AUDIO_BLOCK_ALIGN, PCM_FRAME_BYTES)
            pcm.setInt(Guid.AUDIO_BYTES_PER_SECOND, sampleRate * PCM_FRAME_BYTES)
            setInput(stream, pcm)
        }
        return stream
    }

    private inline fun <T> withMediaType(mf: MfApi, block: (MemorySegment) -> T): T {
        hr(Native.call(mf.createMediaType, D_A, pointer), "MFCreateMediaType")
        val type = pointer.get(ADDRESS, 0L)
        try {
            return block(type)
        } finally {
            release(type)
        }
    }

    private fun addStream(type: MemorySegment): Int {
        hr(writer.com(ADD_STREAM, D_AAA, type, streamIndex), "AddStream")
        return streamIndex.get(INT, 0L)
    }

    private fun setInput(stream: Int, type: MemorySegment) =
        hr(writer.com(SET_INPUT_MEDIA_TYPE, D_AIAA, stream, type, NULL), "SetInputMediaType")

    private inline fun writeSample(stream: Int, length: Int, time: Long, duration: Long, fill: (MemorySegment) -> Unit) {
        val mf = checkNotNull(api)
        hr(Native.call(mf.createMemoryBuffer, D_IA, length, pointer), "MFCreateMemoryBuffer")
        val buffer = pointer.get(ADDRESS, 0L)
        var sample = NULL
        try {
            hr(buffer.com(LOCK, D_AAAA, pointer, NULL, NULL), "Lock")
            try {
                fill(pointer.get(ADDRESS, 0L).reinterpret(length.toLong()))
            } finally {
                buffer.com(UNLOCK, D_A)
            }
            hr(buffer.com(SET_CURRENT_LENGTH, D_AI, length), "SetCurrentLength")
            hr(Native.call(mf.createSample, D_A, pointer), "MFCreateSample")
            sample = pointer.get(ADDRESS, 0L)
            hr(sample.com(ADD_BUFFER, D_AA, buffer), "AddBuffer")
            hr(sample.com(SET_SAMPLE_TIME, D_AL, time), "SetSampleTime")
            hr(sample.com(SET_SAMPLE_DURATION, D_AL, duration), "SetSampleDuration")
            hr(writer.com(WRITE_SAMPLE, D_AIA, stream, sample), "WriteSample")
        } finally {
            release(sample)
            release(buffer)
        }
    }

    private fun MemorySegment.setGuid(key: MemorySegment, value: MemorySegment) = hr(com(SET_GUID, D_AAA, key, value), "SetGUID")

    private fun MemorySegment.setInt(key: MemorySegment, value: Int) = hr(com(SET_UINT32, D_AAI, key, value), "SetUINT32")

    private fun MemorySegment.setLong(key: MemorySegment, value: Long) = hr(com(SET_UINT64, D_AAL, key, value), "SetUINT64")

    private fun release(target: MemorySegment) {
        if (target.address() != 0L) target.com(RELEASE, D_A)
    }

    // Zero-terminated UTF-16 path
    private fun wide(text: String): MemorySegment {
        val chars = arena.allocate((text.length + 1L) * 2, 2L)
        for (i in text.indices) chars.set(ValueLayout.JAVA_CHAR, i * 2L, text[i])
        return chars
    }

    private fun pack(high: Int, low: Int): Long = (high.toLong() shl 32) or low.toLong()

    private companion object {
        const val MF_VERSION = 0x0002_0070
        const val MFSTARTUP_FULL = 0
        const val COINIT_MULTITHREADED = 0
        const val PROGRESSIVE = 2
        const val H264_HIGH = 100
        const val CHANNELS = 2
        const val PCM_BITS = 16
        const val PCM_FRAME_BYTES = 4
        const val BYTES_PER_PIXEL = 4

        // IUnknown and IMFAttributes slots
        const val RELEASE = 2
        const val SET_UINT32 = 21
        const val SET_UINT64 = 22
        const val SET_GUID = 24

        // IMFSinkWriter slots
        const val ADD_STREAM = 3
        const val SET_INPUT_MEDIA_TYPE = 4
        const val BEGIN_WRITING = 5
        const val WRITE_SAMPLE = 6
        const val FINALIZE = 11

        // IMFMediaBuffer slots
        const val LOCK = 3
        const val UNLOCK = 4
        const val SET_CURRENT_LENGTH = 6

        // IMFSample slots
        const val SET_SAMPLE_TIME = 36
        const val SET_SAMPLE_DURATION = 38
        const val ADD_BUFFER = 42
    }
}
