package id.nextfl.app.visual

import android.graphics.Canvas
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/** Beat zoom plus corner keyframes. */
class CameraMotion(seed: Int = SEED) {
    private val random = Random(seed)
    private var punch = 0f
    private var engage = 0f
    private var from = 0
    private var to = 0
    private var elapsed = 0f
    private var running = false
    private var panAmount = 0f
    private var zoomAmount = 0f

    /** Pan in -1..1 of free room. */
    var panX = 0f
        private set
    var panY = 0f
        private set
    var keyScale = 1f
        private set
    var scale = 1f
        private set

    fun update(beat: Boolean, strength: Float, zoom: Float, pan: Float, dt: Float) {
        zoomAmount = zoom
        // Last amount eases out
        if (pan > 0f) panAmount = pan
        punch *= exp(-dt * ZOOM_DECAY)
        if (beat && zoom > 0f) punch = max(punch, strength.coerceIn(0f, 1f))
        // Eases in and out on toggle
        val goal = if (pan > 0f) 1f else 0f
        engage += (goal - engage) * (1f - exp(-dt / ENGAGE_SECONDS))
        if (pan > 0f) advance(dt) else if (engage < IDLE) running = false
        val t = smooth(elapsed / KEYFRAME_SECONDS)
        panX = lerp(CORNER_X[from], CORNER_X[to], t) * engage
        panY = lerp(CORNER_Y[from], CORNER_Y[to], t) * engage
        keyScale = 1f + panAmount * MAX_KEYFRAME_ZOOM * engage
        scale = keyScale * (1f + punch * zoomAmount * MAX_BEAT_ZOOM)
    }

    // Next corner is random, never same
    private fun advance(dt: Float) {
        if (!running) {
            from = random.nextInt(CORNERS)
            to = otherCorner(from)
            elapsed = 0f
            running = true
            return
        }
        elapsed += dt
        while (elapsed >= KEYFRAME_SECONDS) {
            elapsed -= KEYFRAME_SECONDS
            from = to
            to = otherCorner(from)
        }
    }

    private fun otherCorner(corner: Int): Int = (corner + 1 + random.nextInt(CORNERS - 1)) % CORNERS

    /** Returns false when nothing applied. */
    fun apply(canvas: Canvas, width: Int, height: Int): Boolean {
        if (scale <= 1f + EPSILON) return false
        val cx = width / 2f
        val cy = height / 2f
        // Room from keyframe zoom only
        val room = 1f - 1f / keyScale
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx - panX * cx * room, -cy - panY * cy * room)
        return true
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        const val SEED = 11
        const val CORNERS = 4
        val CORNER_X = floatArrayOf(-1f, 1f, -1f, 1f)
        val CORNER_Y = floatArrayOf(-1f, -1f, 1f, 1f)
        const val KEYFRAME_SECONDS = 3f
        const val ENGAGE_SECONDS = 0.4f
        const val IDLE = 0.001f
        const val ZOOM_DECAY = 5f
        const val MAX_BEAT_ZOOM = 0.35f
        const val MAX_KEYFRAME_ZOOM = 0.3f
        const val EPSILON = 0.0005f
    }
}
