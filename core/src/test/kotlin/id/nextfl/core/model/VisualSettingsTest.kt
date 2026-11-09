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
    fun glareIsClampedAndOldProjectsGetDefault() {
        val clamped = Sanitize.visual(VisualSettings(cometGlare = 3f, glitchGlare = -1f, hillGlare = 0.4f))
        assertEquals(1f, clamped.cometGlare)
        assertEquals(0f, clamped.glitchGlare)
        assertEquals(0.4f, clamped.hillGlare)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(VisualSettings.serializer(), """{"glitchAmount":0.3}""")
        assertEquals(VisualSettings.DEFAULT_GLARE, old.cometGlare)
        assertEquals(VisualSettings.DEFAULT_GLARE, old.hillGlare)
    }

    @Test
    fun beatBandsAreClampedAndOldProjectsFollowLow() {
        val clamped = Sanitize.visual(VisualSettings(beatLow = 2f, beatMid = -1f, beatHigh = 0.3f))
        assertEquals(1f, clamped.beatLow)
        assertEquals(0f, clamped.beatMid)
        assertEquals(0.3f, clamped.beatHigh)
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(VisualSettings.serializer(), """{"glitchAmount":0.3}""")
        assertEquals(1f, old.beatLow)
        assertEquals(0f, old.beatMid)
        assertEquals(0f, old.beatHigh)
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
