package id.nextfl.app.ui.playlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import id.nextfl.app.ui.theme.Fonts
import id.nextfl.core.state.EditorState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState

@Composable
fun TimelineCanvas(vm: EditorViewModel, state: EditorState, ui: UiState, playing: Boolean, geometry: TimelineGeometry, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val renderer = remember(geometry) { TimelineRenderer(geometry, density, Fonts.semibold(context)) }
    val scope = rememberCoroutineScope()
    val gestures = remember(geometry) { TimelineGestures(vm, geometry, scope) }
    val livePosition = remember { mutableDoubleStateOf(0.0) }
    val follow by rememberUpdatedState(ui.follow)
    val libraryVersion by vm.libraryVersion.collectAsState()
    val pending by vm.pendingStretches.collectAsState()
    val viewport = vm.viewport

    LaunchedEffect(density) { viewport.ensureZoom(density) }
    // One position per frame, follow and draw
    LaunchedEffect(playing) {
        while (playing) withFrameNanos { now ->
            val position = vm.displayPosition(now)
            if (follow) gestures.followPlayhead(position)
            livePosition.doubleValue = position
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(gestures) { with(gestures) { handleTouches() } }
            .pointerInput(gestures) { with(gestures) { handleWheel() } },
    ) {
        gestures.bind(state, ui.snap, size.width, size.height, lockPan = playing && ui.follow)
        val scene = TimelineScene(
            width = size.width,
            height = size.height,
            scroll = viewport.scrollSeconds,
            scrollY = viewport.scrollY,
            pps = viewport.pixelsPerSecond.coerceAtLeast(1f),
            doc = state.doc,
            assets = state.assets,
            selectedClipId = state.selectedClipId,
            selectedTrackId = state.selectedTrackId,
            playhead = gestures.scrubPosition ?: if (playing) livePosition.doubleValue else vm.position(),
            playheadGrabbed = gestures.playheadGrabbed,
            snapGuide = gestures.snapGuide,
            peaks = { id -> libraryVersion.let { vm.library.peaks(id) } },
            isStretching = { clip, rate -> pending.isNotEmpty() && vm.library.isPending(clip.assetId, rate) },
        )
        drawIntoCanvas { renderer.draw(it.nativeCanvas, scene) }
    }
}
