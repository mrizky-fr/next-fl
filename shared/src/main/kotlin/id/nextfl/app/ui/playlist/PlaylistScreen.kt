package id.nextfl.app.ui.playlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState

@Composable
fun PlaylistScreen(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean) {
    val selectedClip = state.doc.clips.find { it.id == state.selectedClipId }

    Row(Modifier.fillMaxSize()) {
        TimelineStrip(vm, state, ui, playing, Modifier.weight(1f).fillMaxHeight())
        selectedClip?.let { ClipInspector(vm, state, it) }
    }
}

/** Headers plus timeline, no inspector. */
@Composable
fun TimelineStrip(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val geometry = remember(density) { TimelineGeometry(TimelineMetrics(density)) }
    Row(modifier) {
        TrackHeaders(vm, state.doc.tracks, state.selectedTrackId, geometry, playing)
        TimelineCanvas(vm, state, ui, playing, geometry, Modifier.weight(1f))
    }
}

/** Ruler plus one lane. */
val SingleLaneHeight = (TimelineMetrics.RULER_DP + TimelineMetrics.ROW_DP).dp
