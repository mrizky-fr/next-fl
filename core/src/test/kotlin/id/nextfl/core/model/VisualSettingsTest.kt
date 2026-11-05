package id.nextfl.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualSettingsTest {
    @Test
    fun cometCountIsClampedToRange() {
        assertEquals(VisualSettings.MAX_COMETS, Sanitize.visual(VisualSettings(cometCount = 500)).cometCount)
        assertEquals(0, Sanitize.visual(VisualSettings(cometCount = -3)).cometCount)
        assertEquals(9, Sanitize.visual(VisualSettings(cometCount = 9)).cometCount)
    }

    @Test
    fun oldProjectsKeepSixComets() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = json.decodeFromString(VisualSettings.serializer(), """{"particleCount":40}""")
        assertEquals(6, settings.cometCount)
    }
}
