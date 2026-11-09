package id.nextfl.desktop.visual

import id.nextfl.app.visual.BeatHills
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

class BeatHillsTest {
    private val dt = 1f / 60

    @Test
    fun beatOnRaisedHillLiftsAgain() {
        val hills = BeatHills()
        hills.update(true, 1f, DRIVE, dt)
        repeat(30) { hills.update(false, 0f, DRIVE, dt) }
        val settled = hills.level
        var highest = settled
        hills.update(true, 1f, DRIVE, dt)
        repeat(6) {
            hills.update(false, 0f, DRIVE, dt)
            highest = max(highest, hills.level)
        }
        assertTrue(settled > 0.5f, "hill not raised: $settled")
        assertTrue(highest > settled + 0.1f, "second beat ignored: $settled to $highest")
    }

    private companion object {
        const val DRIVE = 0.8f
    }
}
