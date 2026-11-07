package id.nextfl.core.analysis

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeatSwingTest {
    private val dt = 1f / 60f

    // Returns value just before each beat
    private fun run(swing: BeatSwing, interval: Float, beats: Int): List<Float> {
        val frames = (interval / dt).toInt()
        val extremes = ArrayList<Float>()
        repeat(beats) {
            swing.update(beat = true, dt = dt)
            repeat(frames - 1) { swing.update(beat = false, dt = dt) }
            extremes += swing.value
        }
        return extremes
    }

    @Test
    fun steadyBeatsSwingSideToSide() {
        listOf(0.35f, 0.47f, 0.8f).forEach { interval ->
            val extremes = run(BeatSwing(), interval, 12).drop(4)
            extremes.zipWithNext().forEach { (a, b) -> assertTrue(a * b < 0f, "no alternation at $interval: $extremes") }
            extremes.forEach { assertTrue(abs(it) > 0.9f, "short swing at $interval: $extremes") }
        }
    }

    @Test
    fun fastestRightAfterBeat() {
        val swing = BeatSwing()
        run(swing, 0.5f, 6)
        val path = ArrayList<Float>()
        swing.update(beat = true, dt = dt)
        path += swing.value
        repeat(29) {
            swing.update(beat = false, dt = dt)
            path += swing.value
        }
        val early = abs(path[5] - path[0])
        val late = abs(path[29] - path[24])
        assertTrue(early > late * 3, "early $early late $late")
    }

    @Test
    fun strayBeatMidSwingIsIgnored() {
        val swing = BeatSwing()
        run(swing, 0.5f, 6)
        swing.update(beat = true, dt = dt)
        repeat(5) { swing.update(beat = false, dt = dt) }
        val before = swing.value
        swing.update(beat = false, dt = dt)
        val heading = swing.value - before
        swing.update(beat = true, dt = dt)
        repeat(3) { swing.update(beat = false, dt = dt) }
        val after = swing.value
        repeat(3) { swing.update(beat = false, dt = dt) }
        assertTrue((swing.value - after) * heading > 0f, "stray beat reversed swing")
    }

    @Test
    fun settlesToCenterWhenBeatsStop() {
        val swing = BeatSwing()
        run(swing, 0.5f, 6)
        repeat((4f / dt).toInt()) { swing.update(beat = false, dt = dt) }
        assertEquals(0f, swing.value, 0.05f)
    }
}
