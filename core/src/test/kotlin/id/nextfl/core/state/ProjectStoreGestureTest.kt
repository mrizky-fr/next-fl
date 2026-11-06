package id.nextfl.core.state

import id.nextfl.core.edit.ClipOps
import id.nextfl.core.edit.TrackOps
import id.nextfl.core.model.Defaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProjectStoreGestureTest {
    @Test
    fun gestureDoesNotMergeWithNearbyCoalescedEdits() {
        val store = ProjectStore(clock = { 0L })
        store.change("bpm") { it.copy(bpm = 121.0) }
        store.beginGesture()
        store.change("bpm") { it.copy(bpm = 130.0) }
        store.endGesture()
        store.change("bpm") { it.copy(bpm = 131.0) }
        store.undo()
        assertEquals(130.0, store.current.doc.bpm)
        store.undo()
        assertEquals(121.0, store.current.doc.bpm)
    }

    @Test
    fun loadDropsOpenGesture() {
        val store = ProjectStore()
        store.beginGesture()
        store.change { it.copy(bpm = 150.0) }
        val loaded = Defaults.project().copy(bpm = 90.0)
        store.load(loaded, emptyMap())
        store.endGesture()
        assertEquals(90.0, store.current.doc.bpm)
        assertFalse(store.current.history.canUndo)
    }

    @Test
    fun clipDragAcrossTracksIsOneUndoStep() {
        val store = ProjectStore()
        val tracks = store.current.doc.tracks
        val clip = Defaults.clip(tracks[0].id, "x", "X", 0.0, 4.0)
        store.change { ClipOps.add(it, clip) }
        store.beginGesture()
        tracks.forEachIndexed { index, track -> store.change { ClipOps.move(it, clip.id, track.id, index * 2.0) } }
        store.endGesture()
        assertEquals(tracks.last().id, store.current.doc.clips[0].trackId)
        store.undo()
        assertEquals(tracks[0].id, store.current.doc.clips[0].trackId)
        assertEquals(0.0, store.current.doc.clips[0].startBeat)
    }

    @Test
    fun trackReorderUndoes() {
        val store = ProjectStore()
        val ids = store.current.doc.tracks.map { it.id }
        store.change { TrackOps.move(it, ids[0], ids.lastIndex) }
        assertEquals(ids[0], store.current.doc.tracks.last().id)
        store.undo()
        assertEquals(ids, store.current.doc.tracks.map { it.id })
    }
}
