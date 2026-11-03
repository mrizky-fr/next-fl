package id.nextfl.core.edit

import id.nextfl.core.model.AudioAsset
import id.nextfl.core.model.Defaults
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.state.ProjectStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditTest {
    private val assets = mapOf("song" to AudioAsset("song", "Song", 60.0, 120.0))

    private fun project(): ProjectDoc {
        val doc = Defaults.project().copy(bpm = 120.0)
        val clip = Defaults.clip(doc.tracks[0].id, "song", "Song", 4.0, 20.0).copy(id = "a")
        return doc.copy(clips = listOf(clip))
    }

    @Test
    fun spanUsesBeatsAndTempoSync() {
        val doc = project()
        val span = clipSpan(doc.clips[0], assets, 120.0)
        assertEquals(2.0, span.start)
        assertEquals(22.0, span.end)
        val synced = clipSpan(doc.clips[0].copy(syncTempo = true), assets, 150.0)
        assertEquals(1.25, synced.rate)
        assertEquals(16.0, synced.duration)
        assertEquals(22.0, projectEndSeconds(doc, assets))
    }

    @Test
    fun envelopeFadesAndScalesOverlap() {
        val envelope = ClipEnvelope(10.0, 1.0, 2.0, 4.0)
        assertEquals(0.5, envelope.valueAt(1.0), 1e-9)
        assertEquals(1.0, envelope.valueAt(5.0), 1e-9)
        assertEquals(0.5, envelope.valueAt(8.0), 1e-9)
        assertEquals(0.5, ClipEnvelope(1.0, 1.0, 2.0, 2.0).valueAt(0.25), 1e-9)
    }

    @Test
    fun splitCreatesContiguousParts() {
        val doc = ClipOps.split(project(), assets, "a", 12.0, "b")
        val (left, right) = doc.clips
        assertEquals(10.0, left.length, 1e-9)
        assertEquals("b", right.id)
        assertEquals(10.0, right.offset, 1e-9)
        assertEquals(24.0, right.startBeat, 1e-9)
        val unchanged = project()
        assertSame(unchanged, ClipOps.split(unchanged, assets, "a", 30.0, "b"))
    }

    @Test
    fun duplicatePlacesAfterOriginal() {
        val doc = ClipOps.duplicate(project(), assets, "a", "b")
        assertEquals(44.0, doc.clips[1].startBeat, 1e-9)
    }

    @Test
    fun trimRespectsSourceBounds() {
        val clip = project().clips[0]
        val context = ClipOps.TrimContext(rate = 1.0, bpm = 120.0, assetDuration = 60.0)
        val trimmed = ClipOps.trimStart(clip, 5.0, context)
        assertEquals(10.0, trimmed.startBeat, 1e-9)
        assertEquals(3.0, trimmed.offset, 1e-9)
        assertEquals(17.0, trimmed.length, 1e-9)
        assertEquals(0.0, ClipOps.trimStart(clip, 0.0, context).offset, 1e-9)
        assertEquals(10.0, ClipOps.trimEnd(clip, 12.0, context).length, 1e-9)
        assertEquals(60.0, ClipOps.trimEnd(clip, 500.0, context).length, 1e-9)
    }

    @Test
    fun findsFreeTrackAndSoloRules() {
        val doc = project()
        assertEquals(doc.tracks[1].id, ClipOps.findFreeTrackId(doc, assets, 5.0, 10.0))
        assertEquals(doc.tracks[0].id, ClipOps.findFreeTrackId(doc, assets, 30.0, 40.0))
        val soloed = TrackOps.update(doc, doc.tracks[1].id) { it.copy(solo = true) }
        assertFalse(TrackOps.isAudible(soloed.tracks[0], soloed.tracks))
        assertTrue(TrackOps.isAudible(soloed.tracks[1], soloed.tracks))
        assertTrue(TrackOps.remove(doc, doc.tracks[0].id).clips.isEmpty())
    }
}

class ColorMigrationTest {
    @Test
    fun legacyPaletteMapsToBlueFamily() {
        assertEquals(Defaults.TRACK_COLORS[1], Defaults.migrateTrackColor(0xFF8466FF))
        assertEquals(Defaults.TRACK_COLORS[0], Defaults.migrateTrackColor(0xFF6366F1))
        assertEquals(0xFF123456, Defaults.migrateTrackColor(0xFF123456))
    }
}

class ProjectStoreTest {
    @Test
    fun undoRedoAndCoalescing() {
        var now = 0L
        val store = ProjectStore(clock = { now })
        store.change { it.copy(bpm = 100.0) }
        store.change(coalesce = "bpm") { it.copy(bpm = 101.0) }
        now += 100
        store.change(coalesce = "bpm") { it.copy(bpm = 102.0) }
        assertEquals(2, store.current.history.past.size)
        store.undo()
        assertEquals(100.0, store.current.doc.bpm)
        store.redo()
        assertEquals(102.0, store.current.doc.bpm)
    }

    @Test
    fun gestureRecordsSingleStep() {
        val store = ProjectStore()
        store.beginGesture()
        repeat(5) { step -> store.change { it.copy(bpm = 130.0 + step) } }
        store.endGesture()
        assertEquals(1, store.current.history.past.size)
        store.undo()
        assertEquals(ProjectDoc.DEFAULT_BPM, store.current.doc.bpm)
    }

    @Test
    fun selectionClearsWhenClipRemoved() {
        val store = ProjectStore()
        val trackId = store.current.doc.tracks[0].id
        val clip = Defaults.clip(trackId, "x", "X", 0.0, 1.0)
        store.change { ClipOps.add(it, clip) }
        store.selectClip(clip.id)
        store.change { ClipOps.remove(it, clip.id) }
        assertNull(store.current.selectedClipId)
    }
}
