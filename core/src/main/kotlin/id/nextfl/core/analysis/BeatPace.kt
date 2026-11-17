package id.nextfl.core.analysis

import kotlin.math.exp
import kotlin.math.max

/** Steady pace that beats push forward. */
class BeatPace {
    private var boost = 0f

    /** Speed multiplier, 1 when idle. */
    var rate = 1f
        private set

    /** Returns paced time step. */
    fun update(beat: Boolean, strength: Float, sensitivity: Float, dt: Float): Float {
        // Comet: sharp kick, soft tail
        boost *= exp(-dt * DECAY)
        if (beat && sensitivity > 0f) boost = max(boost, strength.coerceIn(0f, 1f) * sensitivity * MAX_BOOST)
        rate = 1f + boost
        return dt * rate
    }

    private companion object {
        const val MAX_BOOST = 5f
        const val DECAY = 4f
    }
}
