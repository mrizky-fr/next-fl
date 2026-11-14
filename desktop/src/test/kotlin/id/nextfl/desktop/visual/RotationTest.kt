package id.nextfl.desktop.visual

import id.nextfl.app.visual.Rotation
import kotlin.test.Test
import kotlin.test.assertEquals

class RotationTest {
    private val logos = Rotation(4.0, 0.8)

    @Test
    fun holdsThenFadesIntoNext() {
        logos.at(1.0)
        assertEquals(0, logos.current(3))
        assertEquals(0f, logos.mix(3))
        logos.at(3.6)
        assertEquals(0.5f, logos.mix(3), 0.001f)
        assertEquals(1, logos.next(3))
        logos.at(4.0)
        assertEquals(1, logos.current(3))
        assertEquals(0f, logos.mix(3))
    }

    @Test
    fun loopsBackToFirst() {
        logos.at(12.5)
        assertEquals(0, logos.current(3))
        logos.at(11.9)
        assertEquals(0, logos.next(3))
    }

    @Test
    fun singleItemNeverFades() {
        logos.at(3.9)
        assertEquals(0f, logos.mix(1))
        assertEquals(0, logos.current(0))
    }

    @Test
    fun backgroundClockIsIndependent() {
        val backgrounds = Rotation(5.0, 1.2)
        logos.at(4.7)
        backgrounds.at(4.7)
        assertEquals(1, logos.current(2))
        assertEquals(0, backgrounds.current(2))
        assertEquals(0.75f, backgrounds.mix(2), 0.001f)
    }
}
