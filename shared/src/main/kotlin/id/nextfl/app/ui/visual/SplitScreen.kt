package id.nextfl.app.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState
import id.nextfl.app.ui.deck.DjController
import id.nextfl.app.ui.playlist.SingleLaneHeight
import id.nextfl.app.ui.playlist.TimelineStrip
import id.nextfl.app.ui.theme.Palette
import id.nextfl.core.state.EditorState

/** Lane above 3D controller. */
@Composable
fun SplitScreen(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean) {
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        TimelineStrip(vm, state, ui, playing, Modifier.fillMaxWidth().height(SingleLaneHeight))
        DjController(vm, state, playing, Modifier.weight(1f).fillMaxWidth())
    }
}
