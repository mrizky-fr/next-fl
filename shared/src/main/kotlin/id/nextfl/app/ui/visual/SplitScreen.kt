package id.nextfl.app.ui.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState
import id.nextfl.app.ui.playlist.SingleLaneHeight
import id.nextfl.app.ui.playlist.TimelineStrip
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.core.state.EditorState

private const val SQUARE_RATIO = 1f
private val PREVIEW_GAP = 8.dp

/** One lane and deck left, square preview right. */
@Composable
fun SplitScreen(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean) {
    val settings by vm.visualSettings.collectAsState()
    val images = rememberRotationImages(vm)
    Row(Modifier.fillMaxSize().background(Palette.Surface)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TimelineStrip(vm, state, ui, playing, Modifier.fillMaxWidth().height(SingleLaneHeight))
            DeckPanel(vm, state, playing, Modifier.weight(1f).fillMaxWidth())
        }
        // Full height, 1:1
        Box(
            Modifier
                .fillMaxHeight()
                .padding(start = PREVIEW_GAP)
                .aspectRatio(SQUARE_RATIO, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(Dimens.Radius))
                .background(Palette.Bg),
        ) {
            VisualizerCanvas(vm, settings, images)
        }
    }
}
