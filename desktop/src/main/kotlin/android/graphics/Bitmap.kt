package android.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import kotlin.math.max
import org.jetbrains.skia.Bitmap as SkBitmap
import org.jetbrains.skia.BlendMode as SkBlendMode
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Image as SkImage
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Shader as SkShader

/** Android Bitmap over a Skia bitmap. */
class Bitmap internal constructor(skia: SkBitmap, mutable: Boolean, val config: Config) {
    internal val skia: SkBitmap = skia
    val width: Int = skia.width
    val height: Int = skia.height

    var isMutable: Boolean = mutable
        private set

    @Volatile
    var isRecycled: Boolean = false
        private set

    private var alpha: Boolean = skia.alphaType != ColorAlphaType.OPAQUE

    // Cached until pixels change
    @Volatile private var image: SkImage? = null
    private var linearShader: SkShader? = null
    private var nearestShader: SkShader? = null

    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }

    val rowBytes: Int get() = skia.rowBytes

    val byteCount: Int get() = rowBytes * height

    val allocationByteCount: Int get() = byteCount

    fun hasAlpha(): Boolean = alpha

    fun setHasAlpha(hasAlpha: Boolean) {
        checkAlive()
        if (hasAlpha == alpha) return
        alpha = hasAlpha
        skia.setAlphaType(if (hasAlpha) ColorAlphaType.PREMUL else ColorAlphaType.OPAQUE)
        notifyPixelsChanged()
    }

    fun recycle() {
        if (isRecycled) return
        synchronized(this) {
            isRecycled = true
            dropImage()
            skia.close()
        }
    }

    /** Unpremultiplied ARGB, like Android. */
    fun getPixel(x: Int, y: Int): Int {
        checkAlive()
        checkPixel(x, y)
        return skia.getColor(x, y)
    }

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        checkAlive()
        if (width == 0 || height == 0) return
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = skia.readPixels(info, width * 4, x, y) ?: throw IllegalArgumentException("Bad pixel region")
        var k = 0
        for (row in 0 until height) {
            var i = offset + row * stride
            repeat(width) {
                val b = bytes[k].toInt() and 0xFF
                val g = bytes[k + 1].toInt() and 0xFF
                val r = bytes[k + 2].toInt() and 0xFF
                val a = bytes[k + 3].toInt() and 0xFF
                pixels[i++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                k += 4
            }
        }
    }

    fun setPixel(x: Int, y: Int, color: Int) {
        checkPixel(x, y)
        setPixels(intArrayOf(color), 0, 1, x, y, 1, 1)
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        checkAlive()
        check(isMutable) { "Bitmap is immutable" }
        if (width == 0 || height == 0) return
        val patch = premultiplied(pixels, offset, stride, width, height)
        SkCanvas(skia).use { it.writePixels(patch, x, y) }
        patch.close()
        notifyPixelsChanged()
    }

    fun eraseColor(color: Int) {
        checkAlive()
        check(isMutable) { "Bitmap is immutable" }
        skia.erase(color)
        notifyPixelsChanged()
    }

    fun copy(config: Config, isMutable: Boolean): Bitmap {
        checkAlive()
        val copy = allocate(width, height, alpha)
        SkCanvas(copy).use { canvas ->
            SkPaint().use { paint ->
                paint.blendMode = SkBlendMode.SRC
                canvas.drawImage(image(), 0f, 0f, paint)
            }
        }
        if (!isMutable) copy.setImmutable()
        return Bitmap(copy, isMutable, config)
    }

    // Rebuilt only after edits
    internal fun image(): SkImage {
        image?.let { return it }
        synchronized(this) {
            checkAlive()
            return image ?: SkImage.makeFromBitmap(skia).also { image = it }
        }
    }

    internal fun shader(linear: Boolean): SkShader {
        val current = image()
        synchronized(this) {
            val cached = if (linear) linearShader else nearestShader
            if (cached != null) return cached
            val shader = current.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, if (linear) LINEAR else NEAREST, null)
            if (linear) linearShader = shader else nearestShader = shader
            return shader
        }
    }

    internal fun notifyPixelsChanged() {
        if (image == null) return
        synchronized(this) { dropImage() }
    }

    internal fun checkAlive() {
        if (isRecycled) throw IllegalStateException("Can't use a recycled bitmap")
    }

    private fun dropImage() {
        linearShader?.close()
        nearestShader?.close()
        image?.close()
        linearShader = null
        nearestShader = null
        image = null
    }

    private fun checkPixel(x: Int, y: Int) {
        require(x in 0 until width) { "x must be < bitmap.width()" }
        require(y in 0 until height) { "y must be < bitmap.height()" }
    }

    companion object {
        internal val LINEAR: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        internal val NEAREST: SamplingMode = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
        private val DOWNSCALE: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

        @JvmStatic fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            checkSize(width, height)
            return Bitmap(allocate(width, height, config != Config.RGB_565), true, config)
        }

        @JvmStatic fun createBitmap(width: Int, height: Int, config: Config, hasAlpha: Boolean): Bitmap {
            checkSize(width, height)
            return Bitmap(allocate(width, height, hasAlpha), true, config)
        }

        /** Colors are unpremultiplied ARGB. */
        @JvmStatic fun createBitmap(colors: IntArray, width: Int, height: Int, config: Config): Bitmap =
            createBitmap(colors, 0, width, width, height, config)

        @JvmStatic fun createBitmap(colors: IntArray, offset: Int, stride: Int, width: Int, height: Int, config: Config): Bitmap {
            checkSize(width, height)
            if (offset + (height - 1) * stride + width > colors.size) throw ArrayIndexOutOfBoundsException()
            val bitmap = premultiplied(colors, offset, stride, width, height)
            bitmap.setImmutable()
            return Bitmap(bitmap, false, config)
        }

        @JvmStatic fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            checkSize(dstWidth, dstHeight)
            val out = allocate(dstWidth, dstHeight, src.alpha)
            SkCanvas(out).use { canvas ->
                canvas.drawImageRect(
                    src.image(), 0f, 0f, src.width.toFloat(), src.height.toFloat(),
                    0f, 0f, dstWidth.toFloat(), dstHeight.toFloat(), if (filter) LINEAR else NEAREST, null, true,
                )
            }
            return Bitmap(out, true, src.config)
        }

        internal fun allocate(width: Int, height: Int, hasAlpha: Boolean): SkBitmap {
            val bitmap = SkBitmap()
            val alphaType = if (hasAlpha) ColorAlphaType.PREMUL else ColorAlphaType.OPAQUE
            check(bitmap.allocPixels(ImageInfo.makeN32(width, height, alphaType))) { "Bitmap allocation failed" }
            bitmap.erase(if (hasAlpha) Color.TRANSPARENT else Color.BLACK)
            return bitmap
        }

        // Decodes, applies EXIF, downscales
        internal fun decode(bytes: ByteArray, maxEdge: Int): Bitmap? {
            Data.makeFromBytes(bytes).use { data ->
                Codec.makeFromData(data).use { codec ->
                    val opaque = codec.alphaType == ColorAlphaType.OPAQUE
                    val raw = SkBitmap()
                    val rawInfo = ImageInfo.makeN32(codec.width, codec.height, if (opaque) ColorAlphaType.OPAQUE else ColorAlphaType.PREMUL)
                    if (!raw.allocPixels(rawInfo)) return null
                    codec.readPixels(raw)
                    val origin = codec.encodedOrigin
                    val swap = origin.swapsWidthHeight()
                    val w = if (swap) codec.height else codec.width
                    val h = if (swap) codec.width else codec.height
                    val scale = maxEdge.toFloat() / max(w, h)
                    if (scale >= 1f && (origin == EncodedOrigin.TOP_LEFT || origin == EncodedOrigin.UNUSED)) {
                        raw.setImmutable()
                        return Bitmap(raw, false, Config.ARGB_8888)
                    }
                    val outW = if (scale < 1f) (w * scale).toInt().coerceAtLeast(1) else w
                    val outH = if (scale < 1f) (h * scale).toInt().coerceAtLeast(1) else h
                    val out = allocate(outW, outH, !opaque)
                    SkCanvas(out).use { canvas ->
                        canvas.scale(outW.toFloat() / w, outH.toFloat() / h)
                        canvas.concat(origin.toMatrix(codec.width, codec.height))
                        SkImage.makeFromBitmap(raw).use { image ->
                            canvas.drawImageRect(
                                image, 0f, 0f, codec.width.toFloat(), codec.height.toFloat(),
                                0f, 0f, codec.width.toFloat(), codec.height.toFloat(), DOWNSCALE, null, true,
                            )
                        }
                    }
                    raw.close()
                    out.setImmutable()
                    return Bitmap(out, false, Config.ARGB_8888)
                }
            }
        }

        private fun checkSize(width: Int, height: Int) {
            require(width > 0) { "width must be > 0" }
            require(height > 0) { "height must be > 0" }
        }

        // Rounds like SkMulDiv255Round
        private fun mul255(value: Int, alpha: Int): Int {
            val product = value * alpha + 128
            return (product + (product shr 8)) shr 8
        }

        /** Unpremul ARGB to premul bitmap. */
        private fun premultiplied(colors: IntArray, offset: Int, stride: Int, width: Int, height: Int): SkBitmap {
            val bytes = ByteArray(width * height * 4)
            var k = 0
            for (row in 0 until height) {
                var i = offset + row * stride
                repeat(width) {
                    val c = colors[i++]
                    val a = c ushr 24
                    var r = (c shr 16) and 0xFF
                    var g = (c shr 8) and 0xFF
                    var b = c and 0xFF
                    if (a != 255) {
                        r = mul255(r, a)
                        g = mul255(g, a)
                        b = mul255(b, a)
                    }
                    bytes[k] = b.toByte()
                    bytes[k + 1] = g.toByte()
                    bytes[k + 2] = r.toByte()
                    bytes[k + 3] = a.toByte()
                    k += 4
                }
            }
            val bitmap = SkBitmap()
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
            check(bitmap.installPixels(info, bytes, width * 4)) { "Bitmap allocation failed" }
            return bitmap
        }
    }
}

/** Desktop bridge: PNG/JPEG/WebP bytes. */
fun Bitmap.Companion.fromEncoded(bytes: ByteArray, maxEdge: Int): Bitmap? =
    try {
        Bitmap.decode(bytes, maxEdge)
    } catch (_: RuntimeException) {
        null
    }

/** Snapshot for Compose Image. */
fun Bitmap.toComposeImageBitmap(): ImageBitmap = image().toComposeImageBitmap()
