package id.nextfl.app.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileDescriptor
import java.nio.ByteBuffer

enum class MuxTrack { VIDEO, AUDIO }

/** Starts once every track is known. */
class Mp4Muxer(descriptor: FileDescriptor, rotate: Boolean) {
    private class Pending(val track: MuxTrack, val data: ByteArray, val info: MediaCodec.BufferInfo)

    private val muxer = MediaMuxer(descriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
        if (rotate) setOrientationHint(ROTATION_DEGREES)
    }
    private val indices = HashMap<MuxTrack, Int>()
    private val pending = ArrayList<Pending>()
    private var started = false

    fun addTrack(track: MuxTrack, format: MediaFormat) {
        check(!started && track !in indices) { "Track $track sudah terdaftar" }
        indices[track] = muxer.addTrack(format)
        if (indices.size < MuxTrack.entries.size) return
        muxer.start()
        started = true
        pending.forEach { muxer.writeSampleData(indices.getValue(it.track), ByteBuffer.wrap(it.data), it.info) }
        pending.clear()
    }

    // Buffers samples until started
    fun write(track: MuxTrack, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        if (started) {
            muxer.writeSampleData(indices.getValue(track), buffer, info)
            return
        }
        val data = ByteArray(info.size).also(buffer::get)
        val copy = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
        pending.add(Pending(track, data, copy))
    }

    fun finish() {
        if (started) muxer.stop()
    }

    // Release also stops if needed
    fun release() {
        runCatching { muxer.release() }
    }

    private companion object {
        const val ROTATION_DEGREES = 90
    }
}
