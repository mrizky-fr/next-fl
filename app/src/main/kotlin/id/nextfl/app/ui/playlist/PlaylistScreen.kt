package id.nextfl.app.ui.playlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState

@Composable
fun PlaylistScreen(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean) {
    val density = LocalDensity.current.density
    val geometry = remember(density) { TimelineGeometry(TimelineMetrics(density)) }
    val selectedClip = state.doc.clips.find { it.id == state.selectedClipId }

    Row(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxHeight()) {
            TrackHeaders(vm, state.doc.tracks, state.selectedTrackId)
            TimelineCanvas(vm, state, ui, playing, geometry, Modifier.weight(1f))
        }
        selectedClip?.let { ClipInspector(vm, state, it) }
    }
}
