package id.nextfl.core.edit

import id.nextfl.core.model.Defaults
import id.nextfl.core.model.ProjectDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MoveOpsTest {
    private fun project(): ProjectDoc {
        val doc = Defaults.project()
        val clip = Defaults.clip(doc.tracks[0].id, "song", "Song", 4.0, 10.0).copy(id = "a")
        return doc.copy(clips = listOf(clip))
    }

    @Test
    fun trackMoveReordersAndKeepsClips() {
        val doc = project()
        val ids = doc.tracks.map { it.id }
        val moved = TrackOps.move(doc, ids[0], 2)
        assertEquals(listOf(ids[1], ids[2], ids[0]), moved.tracks.map { it.id })
        assertEquals(ids[0], moved.clips[0].trackId)
        assertEquals(listOf(ids[2], ids[0], ids[1]), TrackOps.move(doc, ids[2], -5).tracks.map { it.id })
        assertEquals(listOf(ids[1], ids[2], ids[0]), TrackOps.move(doc, ids[0], 99).tracks.map { it.id })
    }

    @Test
    fun trackMoveIgnoresNoOps() {
        val doc = project()
        assertSame(doc, TrackOps.move(doc, "missing", 1))
        assertSame(doc, TrackOps.move(doc, doc.tracks[1].id, 1))
    }

    @Test
    fun clipMoveChangesLaneAndStart() {
        val doc = project()
        val target = doc.tracks[2].id
        val moved = ClipOps.move(doc, "a", target, 8.0).clips[0]
        assertEquals(target, moved.trackId)
        assertEquals(8.0, moved.startBeat)
    }

    @Test
    fun clipMoveGuardsBadInput() {
        val doc = project()
        val stay = ClipOps.move(doc, "a", "missing", -3.0).clips[0]
        assertEquals(doc.tracks[0].id, stay.trackId)
        assertEquals(0.0, stay.startBeat)
        assertEquals(doc, ClipOps.move(doc, "missing", doc.tracks[1].id, 2.0))
    }
}
