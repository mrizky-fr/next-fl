package id.nextfl.core.engine

/** Mixed output ring buffer. */
class MasterTap(private val capacity: Int = 1 shl 15) {
    private val ring = FloatArray(capacity)
    private val mask = (capacity - 1).toLong()

    @Volatile
    var written = 0L
        private set

    init {
        require(capacity and (capacity - 1) == 0) { "Capacity must be a power of two" }
    }

    fun reset() {
        ring.fill(0f)
        written = 0
    }

    fun write(left: FloatArray, right: FloatArray, frames: Int, outputStart: Long) {
        for (i in 0 until frames) ring[((outputStart + i) and mask).toInt()] = (left[i] + right[i]) * 0.5f
        written = outputStart + frames
    }

    /** Reads frames before endExclusive. */
    fun read(endExclusive: Long, dest: FloatArray) {
        val start = endExclusive - dest.size
        val available = written
        for (i in dest.indices) {
            val index = start + i
            dest[i] = if (index < 0 || index >= available || available - index > capacity) 0f else ring[(index and mask).toInt()]
        }
    }
}
