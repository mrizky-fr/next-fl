package id.nextfl.app.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState
import id.nextfl.app.ui.playlist.PlaylistScreen
import id.nextfl.app.ui.theme.Palette

private const val SQUARE_RATIO = 1f

/** Tracks left, square preview right. */
@Composable
fun SplitScreen(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean) {
    val settings by vm.visualSettings.collectAsState()
    val background by vm.background.collectAsState()
    val logo by vm.logo.collectAsState()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { PlaylistScreen(vm, state, ui, playing) }
        // Full track-area height, 1:1
        Box(Modifier.fillMaxHeight().aspectRatio(SQUARE_RATIO, matchHeightConstraintsFirst = true).clipToBounds().background(Palette.Bg)) {
            VisualizerCanvas(vm, settings, background, logo)
        }
    }
}
