package id.nextfl.core.engine

/** Recent block peaks by output frame. */
class PeakHistory(private val capacity: Int = DEFAULT_BLOCKS) {
    private val starts = LongArray(capacity)
    private val ends = LongArray(capacity)
    private val peaks = FloatArray(capacity)

    @Volatile
    private var count = 0L

    // Render thread only
    fun write(start: Long, frames: Int, peak: Float) {
        val slot = (count % capacity).toInt()
        starts[slot] = start
        ends[slot] = start + frames
        peaks[slot] = peak
        count++
    }

    /** Peak of block containing frame. */
    fun at(frame: Long): Float {
        val written = count
        var index = written - 1
        while (index >= maxOf(0L, written - capacity)) {
            val slot = (index % capacity).toInt()
            if (frame >= starts[slot] && frame < ends[slot]) return peaks[slot]
            index--
        }
        return 0f
    }

    fun reset() {
        count = 0
    }

    private companion object {
        // About 1.5 s at 1024
        const val DEFAULT_BLOCKS = 64
    }
}
