package id.nextfl.app.ui.visual

import id.nextfl.app.ui.components.PANEL_PADDING
import id.nextfl.app.ui.components.PANEL_GAP
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Crop169
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import id.nextfl.core.model.AspectRatio
import id.nextfl.core.model.FxRanges
import id.nextfl.core.model.SlideDirection
import id.nextfl.core.model.SocialPlatform
import id.nextfl.core.model.VisualSettings
import kotlin.math.roundToInt
import id.nextfl.app.data.CollabImage
import id.nextfl.app.data.VisualImage
import id.nextfl.app.data.rotationImages
import id.nextfl.app.platform.drawingCanvas
import id.nextfl.app.platform.rememberImagePicker
import id.nextfl.app.platform.toImageBitmap
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
import id.nextfl.app.visual.SocialIconPainter

private val RING_COLORS = listOf(0xFFFFFFFF, 0xFF6AAEEA, 0xFF3C8DDB, 0xFF0465C4, 0xFFFFD166)
// Pink differs from migrated legacy default
private val PARTICLE_COLORS = listOf(0xFFFFFFFF, 0xFFFF8AD8, 0xFF6AAEEA, 0xFF0465C4, 0xFFFF5C8A)
private const val MAX_FRAME_SECONDS = 0.1f
private val SOCIAL_ICON_SIZE = 26.dp
// Bitmap adds shadow padding
private const val SOCIAL_BITMAP_SCALE = 1.6f

@Composable
fun VisualScreen(vm: EditorViewModel, ui: UiState, playing: Boolean) {
    val settings by vm.visualSettings.collectAsState()
    val background by vm.background.collectAsState()
    val logo by vm.logo.collectAsState()
    val images = rememberRotationImages(vm)
    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            AspectFit(settings.aspect) { VisualizerCanvas(vm, settings, images) }
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

/** Main image first, then collab extras. */
class RotationImages(val backgrounds: List<Bitmap>, val logos: List<Bitmap>)

@Composable
fun rememberRotationImages(vm: EditorViewModel): RotationImages {
    val background by vm.background.collectAsState()
    val logo by vm.logo.collectAsState()
    val extraBackgrounds by vm.collabBackgrounds.collectAsState()
    val extraLogos by vm.collabLogos.collectAsState()
    return remember(background, logo, extraBackgrounds, extraLogos) {
        RotationImages(rotationImages(background, extraBackgrounds), rotationImages(logo, extraLogos))
    }
}

@Composable
fun VisualizerCanvas(vm: EditorViewModel, settings: VisualSettings, images: RotationImages) {
    val template = remember { PulseRingTemplate(vm.canvasFonts.bold) }
    LaunchedEffect(images) { template.setImages(images.backgrounds, images.logos) }
    val tick by rememberFrameTick()
    val last = remember { mutableLongStateOf(0L) }
    Canvas(Modifier.fillMaxSize()) {
        val now = tick
        val dt = if (last.longValue == 0L) 1 / 60f else ((now - last.longValue) / 1e9f).coerceIn(0f, MAX_FRAME_SECONDS)
        last.longValue = now
        val frame = vm.analysis.next(dt, settings)
        drawIntoCanvas { template.draw(it.drawingCanvas(), size.width.toInt(), size.height.toInt(), frame, dt, settings) }
    }
}

@Composable
private fun VisualSettingsPanel(vm: EditorViewModel, settings: VisualSettings, background: Bitmap?, logo: Bitmap?) {
    fun update(transform: (VisualSettings) -> VisualSettings) = vm.updateVisual(transform)
    val collabLogos by vm.collabLogos.collectAsState()
    val collabBackgrounds by vm.collabBackgrounds.collectAsState()
    Column(
        modifier = Modifier.width(300.dp).fillMaxHeight().background(Palette.Surface).verticalScroll(rememberScrollState()).padding(PANEL_PADDING),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        PanelSection("Template") {
            Text("Pulse Ring", style = TextStyles.Title)
        }
        PanelSection("Rasio") {
            SegmentedControl(AspectRatio.entries, settings.aspect, { it.label }, { aspect -> update { it.copy(aspect = aspect) } }, icon = ::aspectIcon)
        }
        PanelSection("Gambar") {
            ImagePickerRow(vm, VisualImage.LOGO, "Logo DJ", logo, RoundedCornerShape(Dimens.Radius))
            ImagePickerRow(vm, VisualImage.BACKGROUND, "Background", background, RoundedCornerShape(Dimens.Radius))
        }
        PanelSection("Teks") {
            TextInput(settings.title, { text -> update { it.copy(title = text.take(VisualSettings.MAX_TITLE)) } }, Modifier.fillMaxWidth(), placeholder = "Judul lagu / nama DJ (opsional)")
            ToggleRow("Sosmed & username", settings.showSocial, { on -> update { it.copy(showSocial = on) } })
            if (settings.showSocial) {
                TextInput(
                    settings.socialUsername,
                    { text -> update { it.copy(socialUsername = text.take(VisualSettings.MAX_USERNAME)) } },
                    Modifier.fillMaxWidth(),
                    placeholder = "@username",
                )
                SocialPicker(settings.socialPlatforms) { platform ->
                    update { it.copy(socialPlatforms = if (platform in it.socialPlatforms) it.socialPlatforms - platform else it.socialPlatforms + platform) }
                }
            }
        }
        PanelSection("Kolaborasi") {
            Text("Logo bergantian dengan Logo DJ tiap 4 detik", style = TextStyles.Caption)
            CollabImageList(vm, VisualImage.LOGO, "Logo", collabLogos)
            Text("Teks berganti bareng logo", style = TextStyles.Caption)
            CollabTextList(
                settings.collabTexts,
                onEdit = { index, text -> update { it.copy(collabTexts = it.collabTexts.mapIndexed { i, old -> if (i == index) text.take(VisualSettings.MAX_TITLE) else old }) } },
                onRemove = { index -> update { it.copy(collabTexts = it.collabTexts.filterIndexed { i, _ -> i != index }) } },
                onAdd = { update { if (it.collabTexts.size >= VisualSettings.MAX_COLLAB) it else it.copy(collabTexts = it.collabTexts + "") } },
            )
            Text("Background bergantian tiap 5 detik", style = TextStyles.Caption)
            CollabImageList(vm, VisualImage.BACKGROUND, "Background", collabBackgrounds)
            Text("Masuk dari", style = TextStyles.Caption)
            SegmentedControl(SlideDirection.entries, settings.backgroundSlide, { it.label }, { side -> update { it.copy(backgroundSlide = side) } })
        }
        PanelSection("Warna") {
            Text("Ring", style = TextStyles.Caption)
            ColorSwatches(RING_COLORS, settings.ringColor) { color -> update { it.copy(ringColor = color) } }
            Text("Partikel & kilatan", style = TextStyles.Caption)
            ColorSwatches(PARTICLE_COLORS, settings.particleColor) { color -> update { it.copy(particleColor = color) } }
        }
        PanelSection("Elemen") {
            ToggleRow("Logo", settings.showLogo, { on -> update { it.copy(showLogo = on) } })
            ToggleRow("Gunung beat", settings.showHills, { on -> update { it.copy(showHills = on) } })
            ToggleRow("Komet segitiga", settings.streaks, { on -> update { it.copy(streaks = on) } })
        }
        PanelSection("Efek beat") {
            ValueSlider("Riak", settings.rippleAmount, FxRanges.UNIT, { v -> update { it.copy(rippleAmount = v) } }, ::formatIntensity)
            ValueSlider("Pendulum", settings.pendulumAmount, FxRanges.UNIT, { v -> update { it.copy(pendulumAmount = v) } }, ::formatIntensity)
            ValueSlider("Glitch", settings.glitchAmount, FxRanges.UNIT, { v -> update { it.copy(glitchAmount = v) } }, ::formatIntensity)
        }
        PanelSection("Cahaya") {
            ValueSlider("Komet", settings.cometGlare, FxRanges.UNIT, { v -> update { it.copy(cometGlare = v) } }, ::formatIntensity)
            ValueSlider("Glitch", settings.glitchGlare, FxRanges.UNIT, { v -> update { it.copy(glitchGlare = v) } }, ::formatIntensity)
            ValueSlider("Gunung beat", settings.hillGlare, FxRanges.UNIT, { v -> update { it.copy(hillGlare = v) } }, ::formatIntensity)
        }
        PanelSection("Gerak") {
            ValueSlider("Sensitivitas beat", settings.sensitivity, 0.3f..2.5f, { v -> update { it.copy(sensitivity = v) } }, { "%.1f×".format(it) })
            ValueSlider("Low", settings.beatLow, FxRanges.UNIT, { v -> update { it.copy(beatLow = v) } }, ::formatIntensity)
            ValueSlider("Mid", settings.beatMid, FxRanges.UNIT, { v -> update { it.copy(beatMid = v) } }, ::formatIntensity)
            ValueSlider("High", settings.beatHigh, FxRanges.UNIT, { v -> update { it.copy(beatHigh = v) } }, ::formatIntensity)
            ValueSlider("Sinkron", settings.syncMs.toFloat(), 0f..VisualSettings.MAX_SYNC_MS.toFloat(), { v -> update { it.copy(syncMs = v.roundToInt()) } }, { "${it.roundToInt()} ms" })
            ValueSlider("Jumlah partikel", settings.particleCount.toFloat(), 0f..200f, { v -> update { it.copy(particleCount = v.toInt()) } }, { it.toInt().toString() })
            ValueSlider("Jumlah komet", settings.cometCount.toFloat(), 0f..VisualSettings.MAX_COMETS.toFloat(), { v -> update { it.copy(cometCount = v.roundToInt()) } }, { it.roundToInt().toString() })
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
private fun SocialPicker(selected: Set<SocialPlatform>, onToggle: (SocialPlatform) -> Unit) {
    val iconPx = with(LocalDensity.current) { SOCIAL_ICON_SIZE.roundToPx() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SocialPlatform.entries.forEach { platform ->
            val on = platform in selected
            val bitmap = remember(platform, iconPx) { SocialIconPainter.render(platform, iconPx).toImageBitmap() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.Radius))
                    .background(if (on) Palette.ControlHigh else Palette.Well)
                    .clickable(role = Role.Checkbox) { onToggle(platform) }
                    .padding(vertical = 4.dp)
                    .alpha(if (on) 1f else 0.35f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bitmap, contentDescription = platform.label, modifier = Modifier.size(SOCIAL_ICON_SIZE * SOCIAL_BITMAP_SCALE))
                Text(platform.label, style = TextStyles.Caption, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ImagePickerRow(vm: EditorViewModel, kind: VisualImage, label: String, bitmap: Bitmap?, shape: Shape) {
    val pickImage = rememberImagePicker { file -> vm.setImage(kind, file) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
        Box(Modifier.size(36.dp).clip(shape).background(Palette.Well)) {
            bitmap?.let { Image(it.toImageBitmap(), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(label, style = TextStyles.Body, modifier = Modifier.weight(1f))
        FlatButton(if (bitmap == null) "Pilih" else "Ganti", Icons.Rounded.AddPhotoAlternate, pickImage)
        if (bitmap != null) FlatButton("Hapus", Icons.Rounded.Delete, { vm.removeImage(kind) }, variant = ButtonVariant.GHOST)
    }
}

// Numbered after the main image
@Composable
private fun CollabImageList(vm: EditorViewModel, kind: VisualImage, label: String, images: List<CollabImage>) {
    val pickImage = rememberImagePicker { file -> vm.addCollabImage(kind, file) }
    images.forEachIndexed { index, image ->
        key(image.id) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
                val thumbnail = remember(image.bitmap) { image.bitmap.toImageBitmap() }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(Dimens.Radius)).background(Palette.Well)) {
                    Image(thumbnail, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Text("$label ${index + 2}", style = TextStyles.Body, modifier = Modifier.weight(1f))
                FlatButton("Hapus", Icons.Rounded.Delete, { vm.removeCollabImage(kind, image.id) }, variant = ButtonVariant.GHOST)
            }
        }
    }
    if (images.size < VisualSettings.MAX_COLLAB) FlatButton("Tambah ${label.lowercase()}", Icons.Rounded.AddPhotoAlternate, pickImage)
}

@Composable
private fun CollabTextList(texts: List<String>, onEdit: (Int, String) -> Unit, onRemove: (Int) -> Unit, onAdd: () -> Unit) {
    texts.forEachIndexed { index, text ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
            TextInput(text, { value -> onEdit(index, value) }, Modifier.weight(1f), placeholder = "Teks ${index + 2}")
            IconAction(Icons.Rounded.Delete, "Hapus teks", { onRemove(index) })
        }
    }
    if (texts.size < VisualSettings.MAX_COLLAB) FlatButton("Tambah teks", Icons.Rounded.Add, onAdd)
}

private fun aspectIcon(aspect: AspectRatio) = when (aspect) {
    AspectRatio.WIDE -> Icons.Rounded.Crop169
    AspectRatio.PORTRAIT -> Icons.Rounded.CropPortrait
    AspectRatio.SQUARE -> Icons.Rounded.CropSquare
}

// Zero reads as off
private fun formatIntensity(value: Float): String = if (value < 0.01f) "Mati" else "${(value * 100).roundToInt()}%"
