package id.nextfl.core.engine

import id.nextfl.core.pcm.StereoSink
import kotlin.math.min

object OfflineRenderer {
    // Matches live block: identical ramps
    private const val BLOCK = 512

    fun totalFrames(graph: RenderGraph): Long = if (graph.endFrame == 0L) 0L else graph.endFrame + graph.tailFrames

    /** Returns false when cancelled. */
    fun render(
        graph: RenderGraph,
        sampleRate: Int,
        sink: StereoSink,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Boolean {
        val engine = MixEngine(sampleRate, BLOCK)
        engine.setGraph(graph)
        engine.resetOutputClock(0)
        val total = totalFrames(graph)
        val left = FloatArray(BLOCK)
        val right = FloatArray(BLOCK)
        var done = 0L
        while (done < total) {
            if (isCancelled()) return false
            val frames = min(BLOCK.toLong(), total - done).toInt()
            engine.renderStereo(left, right, frames)
            sink.write(left, right, frames)
            done += frames
            onProgress(done.toFloat() / total)
        }
        return true
    }
}
