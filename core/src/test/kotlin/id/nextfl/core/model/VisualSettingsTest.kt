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
        assertEquals(false, settings.showSocial)
        assertEquals(SocialPlatform.entries.toSet(), settings.socialPlatforms)
    }

    @Test
    fun socialSettingsSurviveRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val original = VisualSettings(showSocial = true, socialUsername = "@djkyy", socialPlatforms = setOf(SocialPlatform.TIKTOK, SocialPlatform.SPOTIFY))
        val decoded = json.decodeFromString(VisualSettings.serializer(), json.encodeToString(VisualSettings.serializer(), original))
        assertEquals(original, decoded)
        assertEquals(VisualSettings.MAX_USERNAME, Sanitize.visual(VisualSettings(socialUsername = "x".repeat(90))).socialUsername.length)
    }
}
