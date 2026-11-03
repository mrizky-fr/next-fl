package id.nextfl.app.ui.visual

import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Crop169
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import id.nextfl.app.ui.theme.Fonts
import androidx.compose.ui.unit.dp
import id.nextfl.core.model.AspectRatio
import id.nextfl.core.model.VisualSettings
import id.nextfl.app.data.VisualImage
import id.nextfl.app.editor.EditorViewModel
import id.nextfl.app.editor.UiState
import id.nextfl.app.ui.components.ButtonVariant
import id.nextfl.app.ui.components.ColorSwatches
import id.nextfl.app.ui.components.FlatButton
import id.nextfl.app.ui.components.IconAction
import id.nextfl.app.ui.components.PanelSection
import id.nextfl.app.ui.components.SegmentedControl
import id.nextfl.app.ui.components.TextInput
import id.nextfl.app.ui.components.ToggleRow
import id.nextfl.app.ui.components.ValueSlider
import id.nextfl.app.ui.components.rememberFrameTick
import id.nextfl.app.ui.theme.Dimens
import id.nextfl.app.ui.theme.Palette
import id.nextfl.app.ui.theme.TextStyles
import id.nextfl.app.visual.PulseRingTemplate

private val RING_COLORS = listOf(0xFFFFFFFF, 0xFF6AAEEA, 0xFF3C8DDB, 0xFF0465C4, 0xFFFFD166)
private val PARTICLE_COLORS = listOf(0xFFF3A6FF, 0xFFFFFFFF, 0xFF6AAEEA, 0xFF0465C4, 0xFFFF5C8A)
private const val MAX_FRAME_SECONDS = 0.1f

@Composable
fun VisualScreen(vm: EditorViewModel, ui: UiState, playing: Boolean) {
    val settings by vm.visualSettings.collectAsState()
    val background by vm.background.collectAsState()
    val logo by vm.logo.collectAsState()
    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            AspectFit(settings.aspect) { VisualizerCanvas(vm, settings, background, logo) }
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", vm::togglePlay, variant = ButtonVariant.SOLID)
                IconAction(
                    if (ui.visualFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    "Layar penuh",
                    { vm.updateUi { it.copy(visualFullscreen = !it.visualFullscreen) } },
                    variant = ButtonVariant.SOLID,
                )
            }
        }
        if (!ui.visualFullscreen) {
            VisualSettingsPanel(vm, settings, background, logo)
        }
    }
}

@Composable
private fun AspectFit(aspect: AspectRatio, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = aspect.width.toFloat() / aspect.height
        val byWidth = maxWidth / ratio <= maxHeight
        val width = if (byWidth) maxWidth else maxHeight * ratio
        val height = if (byWidth) maxWidth / ratio else maxHeight
        Box(
            Modifier.size(width, height).clip(RoundedCornerShape(Dimens.Radius)),
        ) { content() }
    }
}

@Composable
fun VisualizerCanvas(vm: EditorViewModel, settings: VisualSettings, background: Bitmap?, logo: Bitmap?) {
    val context = LocalContext.current
    val template = remember { PulseRingTemplate(Fonts.bold(context)) }
    LaunchedEffect(background, logo) { template.setImages(background, logo) }
    val tick by rememberFrameTick()
    val last = remember { mutableLongStateOf(0L) }
    Canvas(Modifier.fillMaxSize()) {
        val now = tick
        val dt = if (last.longValue == 0L) 1 / 60f else ((now - last.longValue) / 1e9f).coerceIn(0f, MAX_FRAME_SECONDS)
        last.longValue = now
        val frame = vm.analysis.next(dt, settings.sensitivity)
        drawIntoCanvas { template.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), frame, dt, settings) }
    }
}

@Composable
private fun VisualSettingsPanel(vm: EditorViewModel, settings: VisualSettings, background: Bitmap?, logo: Bitmap?) {
    fun update(transform: (VisualSettings) -> VisualSettings) = vm.updateVisual(transform)
    Column(
        modifier = Modifier.width(300.dp).fillMaxHeight().background(Palette.Surface).verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PanelSection("Template") {
            Text("Pulse Ring", style = TextStyles.Title)
            Text("Logo bulat, ring spektrum, partikel dan komet berasap yang bergerak mengikuti beat.", style = TextStyles.Caption)
        }
        PanelSection("Rasio") {
            SegmentedControl(AspectRatio.entries, settings.aspect, { it.label }, { aspect -> update { it.copy(aspect = aspect) } }, icon = ::aspectIcon)
        }
        PanelSection("Gambar") {
            ImagePickerRow(vm, VisualImage.LOGO, "Logo DJ", logo, RoundedCornerShape(Dimens.Radius))
            ImagePickerRow(vm, VisualImage.BACKGROUND, "Background", background, RoundedCornerShape(Dimens.Radius))
        }
        PanelSection("Teks") {
            TextInput(settings.title, { text -> update { it.copy(title = text.take(80)) } }, Modifier.fillMaxWidth(), placeholder = "Judul lagu / nama DJ (opsional)")
        }
        PanelSection("Warna") {
            Text("Ring", style = TextStyles.Caption)
            ColorSwatches(RING_COLORS, settings.ringColor) { color -> update { it.copy(ringColor = color) } }
            Text("Partikel & kilatan", style = TextStyles.Caption)
            ColorSwatches(PARTICLE_COLORS, settings.particleColor) { color -> update { it.copy(particleColor = color) } }
        }
        PanelSection("Gerak") {
            ValueSlider("Sensitivitas beat", settings.sensitivity, 0.3f..2.5f, { v -> update { it.copy(sensitivity = v) } }, { "%.1f×".format(it) })
            ValueSlider("Jumlah partikel", settings.particleCount.toFloat(), 0f..200f, { v -> update { it.copy(particleCount = v.toInt()) } }, { it.toInt().toString() })
            ToggleRow("Komet segitiga", settings.streaks, { on -> update { it.copy(streaks = on) } })
            ToggleRow("Goyang saat beat", settings.shake, { on -> update { it.copy(shake = on) } })
        }
        PanelSection("Background") {
            ValueSlider("Gelap", settings.backgroundDim, 0f..0.95f, { v -> update { it.copy(backgroundDim = v) } }, { "${(it * 100).toInt()}%" })
            ValueSlider("Blur", settings.backgroundBlur, 0f..20f, { v -> update { it.copy(backgroundBlur = v) } }, { it.toInt().toString() })
            ToggleRow("Hitam putih", settings.grayscale, { on -> update { it.copy(grayscale = on) } })
        }
    }
}

@Composable
private fun ImagePickerRow(vm: EditorViewModel, kind: VisualImage, label: String, bitmap: Bitmap?, shape: Shape) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.setImage(kind, it) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(44.dp).clip(shape).background(Palette.Well)) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.Body)
            Text(if (bitmap == null) "Belum dipilih" else "${bitmap.width}×${bitmap.height}", style = TextStyles.Caption)
        }
        FlatButton(if (bitmap == null) "Pilih" else "Ganti", Icons.Rounded.AddPhotoAlternate, {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        if (bitmap != null) FlatButton("Hapus", Icons.Rounded.Delete, { vm.removeImage(kind) }, variant = ButtonVariant.GHOST)
    }
}

private fun aspectIcon(aspect: AspectRatio) = when (aspect) {
    AspectRatio.WIDE -> Icons.Rounded.Crop169
    AspectRatio.PORTRAIT -> Icons.Rounded.CropPortrait
    AspectRatio.SQUARE -> Icons.Rounded.CropSquare
}
