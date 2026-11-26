package id.nextfl.app.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.ui.deck.DjController
import id.nextfl.app.ui.theme.Palette
import id.nextfl.core.state.EditorState

/** Full-screen 3D DJ controller. */
@Composable
fun SplitScreen(vm: EditorViewModel, state: EditorState, playing: Boolean) {
    DjController(vm, state, playing, Modifier.fillMaxSize().background(Palette.Bg))
}
