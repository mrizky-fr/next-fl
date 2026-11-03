package id.nextfl.app.ui

import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.ButtonVariant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nextfl.app.R
import id.nextfl.app.editor.AppView
import id.nextfl.app.editor.BusyState
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.Toast
import id.nextfl.app.editor.ToastTone
import id.nextfl.app.ui.mixer.MixerScreen
import id.nextfl.app.ui.playlist.PlaylistScreen
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import id.nextfl.app.ui.topbar.TopBar
import id.nextfl.app.ui.visual.SplitScreen
import id.nextfl.app.ui.visual.VisualScreen

private val CardShape = RoundedCornerShape(Dimens.Radius)

@Composable
fun AppRoot(vm: EditorViewModel) {
    val ready by vm.ready.collectAsState()
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        if (ready) Editor(vm) else Splash()
        Overlays(vm)
    }
}

@Composable
private fun Editor(vm: EditorViewModel) {
    val state by vm.editor.collectAsState()
    val ui by vm.ui.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }
    val fullscreenVisual = ui.view == AppView.VISUAL && ui.visualFullscreen
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        if (!fullscreenVisual) TopBar(vm, ui.view, state.doc.bpm, state.history.canUndo, state.history.canRedo, playing)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (ui.view) {
                AppView.PLAYLIST -> PlaylistScreen(vm, state, ui, playing)
                AppView.MIXER -> MixerScreen(vm, state)
                AppView.VISUAL -> VisualScreen(vm, ui, playing)
                AppView.SPLIT -> SplitScreen(vm, state, ui, playing)
            }
        }
    }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.ic_logo_glyph), contentDescription = null, modifier = Modifier.size(56.dp))
        LinearProgressIndicator(
            color = Palette.Accent,
            trackColor = Palette.Control,
            strokeCap = StrokeCap.Butt,
            modifier = Modifier.padding(top = 20.dp).width(120.dp).height(3.dp),
        )
    }
}

@Composable
private fun Overlays(vm: EditorViewModel) {
    val busy by vm.busy.collectAsState()
    val toasts by vm.toasts.collectAsState()
    busy?.let { BusyOverlay(it) }
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            toasts.forEach { toast -> ToastCard(toast, onDismiss = { vm.dismissToast(toast.id) }) }
        }
    }
}

@Composable
private fun BusyOverlay(busy: BusyState) {
    Box(Modifier.fillMaxSize().background(Palette.Well.copy(alpha = 0.75f)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(300.dp).clip(CardShape).background(Palette.Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(busy.message, style = TextStyles.Body, maxLines = 2)
            val progress = busy.progress
            if (progress == null) {
                LinearProgressIndicator(color = Palette.Accent, trackColor = Palette.Control, strokeCap = StrokeCap.Butt, modifier = Modifier.fillMaxWidth().height(3.dp))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Palette.Accent,
                    trackColor = Palette.Control,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
                Text("${(progress * 100).toInt()}%", style = TextStyles.LcdSmall)
            }
        }
    }
}

@Composable
private fun ToastCard(toast: Toast, onDismiss: () -> Unit) {
    val (icon, tint) = when (toast.tone) {
        ToastTone.INFO -> Icons.Rounded.Info to Palette.Accent
        ToastTone.SUCCESS -> Icons.Rounded.CheckCircle to Palette.Solo
        ToastTone.ERROR -> Icons.Rounded.ErrorOutline to Palette.Danger
    }
    Row(
        modifier = Modifier
            .width(420.dp)
            .clip(CardShape)
            .background(Palette.Control)
            .clickable(role = Role.Button, onClickLabel = "Tutup", onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(toast.message, style = TextStyles.Body, modifier = Modifier.weight(1f))
        toast.action?.let { action ->
            FlatButton(action.label, Icons.Rounded.Share, { action.run(); onDismiss() }, variant = ButtonVariant.ACCENT)
        }
    }
}
