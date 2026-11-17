package id.nextfl.core.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatPaceTest {
    private val dt = 1f / 60f

    private fun travel(pace: BeatPace, beats: Set<Int>, sensitivity: Float): Float {
        var total = 0f
        repeat(180) { frame -> total += pace.update(frame in beats, 1f, sensitivity, dt) }
        return total
    }

    @Test
    fun keepsBaseSpeedWithoutBeats() {
        assertEquals(3f, travel(BeatPace(), emptySet(), 1f), 0.01f)
    }

    @Test
    fun eachBeatPushesFurther() {
        val one = travel(BeatPace(), setOf(30), 1f)
        val three = travel(BeatPace(), setOf(30, 90, 150), 1f)
        assertTrue(one > 3.5f, "one beat $one")
        assertTrue(three > one + 1f, "three beats $three vs $one")
    }

    @Test
    fun zeroSensitivityIgnoresBeats() {
        assertEquals(3f, travel(BeatPace(), setOf(30, 90), 0f), 0.01f)
    }

    @Test
    fun boostFadesBackToBase() {
        val pace = BeatPace()
        pace.update(true, 1f, 1f, dt)
        assertTrue(pace.rate > 5f)
        repeat(180) { pace.update(false, 1f, 1f, dt) }
        assertEquals(1f, pace.rate, 0.01f)
    }
}
