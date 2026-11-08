package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.TextBlob
import kotlin.math.ceil
import kotlin.math.floor
import org.jetbrains.skia.Paint as SkPaint

/** Android Paint over a Skia paint. */
open class Paint(flags: Int) {
    internal val skia = SkPaint()

    constructor() : this(0)

    constructor(paint: Paint) : this(0) {
        set(paint)
    }

    enum class Style(internal val skia: PaintMode) {
        FILL(PaintMode.FILL),
        STROKE(PaintMode.STROKE),
        FILL_AND_STROKE(PaintMode.STROKE_AND_FILL),
    }

    enum class Cap(internal val skia: PaintStrokeCap) {
        BUTT(PaintStrokeCap.BUTT),
        ROUND(PaintStrokeCap.ROUND),
        SQUARE(PaintStrokeCap.SQUARE),
    }

    enum class Join(internal val skia: PaintStrokeJoin) {
        MITER(PaintStrokeJoin.MITER),
        ROUND(PaintStrokeJoin.ROUND),
        BEVEL(PaintStrokeJoin.BEVEL),
    }

    enum class Align { LEFT, CENTER, RIGHT }

    class FontMetrics {
        @JvmField var top = 0f
        @JvmField var ascent = 0f
        @JvmField var descent = 0f
        @JvmField var bottom = 0f
        @JvmField var leading = 0f
    }

    var flags: Int = 0
        set(value) {
            if (value == field) return
            field = value
            skia.isAntiAlias = (value and ANTI_ALIAS_FLAG) != 0
            skia.isDither = (value and DITHER_FLAG) != 0
            fontDirty = true
        }

    var isAntiAlias: Boolean
        get() = (flags and ANTI_ALIAS_FLAG) != 0
        set(value) = setFlag(ANTI_ALIAS_FLAG, value)

    var isFilterBitmap: Boolean
        get() = (flags and FILTER_BITMAP_FLAG) != 0
        set(value) = setFlag(FILTER_BITMAP_FLAG, value)

    var isDither: Boolean
        get() = (flags and DITHER_FLAG) != 0
        set(value) = setFlag(DITHER_FLAG, value)

    var isFakeBoldText: Boolean
        get() = (flags and FAKE_BOLD_TEXT_FLAG) != 0
        set(value) = setFlag(FAKE_BOLD_TEXT_FLAG, value)

    var isSubpixelText: Boolean
        get() = (flags and SUBPIXEL_TEXT_FLAG) != 0
        set(value) = setFlag(SUBPIXEL_TEXT_FLAG, value)

    var isLinearText: Boolean
        get() = (flags and LINEAR_TEXT_FLAG) != 0
        set(value) = setFlag(LINEAR_TEXT_FLAG, value)

    var isUnderlineText: Boolean
        get() = (flags and UNDERLINE_TEXT_FLAG) != 0
        set(value) = setFlag(UNDERLINE_TEXT_FLAG, value)

    var isStrikeThruText: Boolean
        get() = (flags and STRIKE_THRU_TEXT_FLAG) != 0
        set(value) = setFlag(STRIKE_THRU_TEXT_FLAG, value)

    // Cached color, no JNI read
    private var argb: Int = Color.BLACK

    var color: Int
        get() = argb
        set(value) {
            if (value == argb) return
            argb = value
            skia.color = value
        }

    /** Changes alpha only. */
    var alpha: Int
        get() = argb ushr 24
        set(value) {
            color = (argb and 0x00FFFFFF) or ((value and 0xFF) shl 24)
        }

    fun setARGB(a: Int, r: Int, g: Int, b: Int) {
        color = Color.argb(a, r, g, b)
    }

    var style: Style = Style.FILL
        set(value) {
            field = value
            skia.mode = value.skia
        }

    var strokeWidth: Float = 0f
        set(value) {
            field = value
            skia.strokeWidth = value
        }

    var strokeMiter: Float = DEFAULT_MITER
        set(value) {
            field = value
            skia.strokeMiter = value
        }

    var strokeCap: Cap = Cap.BUTT
        set(value) {
            field = value
            skia.strokeCap = value.skia
        }

    var strokeJoin: Join = Join.MITER
        set(value) {
            field = value
            skia.strokeJoin = value.skia
        }

    var shader: Shader? = null
        set(value) {
            field = value
            skia.shader = value?.skia
        }

    var colorFilter: ColorFilter? = null
        set(value) {
            field = value
            skia.colorFilter = value?.skia
        }

    var maskFilter: MaskFilter? = null
        set(value) {
            field = value
            skia.maskFilter = value?.skia
        }

    var pathEffect: PathEffect? = null
        set(value) {
            field = value
            skia.pathEffect = value?.skia
        }

    // Xfermode and blendMode share state
    private var xfermodeField: Xfermode? = null
    private var blendModeField: BlendMode? = null

    var xfermode: Xfermode?
        get() = xfermodeField
        set(value) {
            xfermodeField = value
            applyBlend(value?.blend)
        }

    var blendMode: BlendMode?
        get() = blendModeField
        set(value) {
            xfermodeField = null
            applyBlend(value)
        }

    private fun applyBlend(mode: BlendMode?) {
        blendModeField = mode
        skia.blendMode = (mode ?: BlendMode.SRC_OVER).skia
    }

    var typeface: Typeface? = null
        set(value) {
            if (value === field) return
            field = value
            fontDirty = true
        }

    var textSize: Float = DEFAULT_TEXT_SIZE
        set(value) {
            if (value == field) return
            field = value
            fontDirty = true
        }

    var textScaleX: Float = 1f
        set(value) {
            if (value == field) return
            field = value
            fontDirty = true
        }

    var textSkewX: Float = 0f
        set(value) {
            if (value == field) return
            field = value
            fontDirty = true
        }

    /** In em, like Android. */
    var letterSpacing: Float = 0f
        set(value) {
            if (value == field) return
            field = value
            stamp++
        }

    var textAlign: Align = Align.LEFT

    var shadowLayerRadius: Float = 0f
        private set
    var shadowLayerDx: Float = 0f
        private set
    var shadowLayerDy: Float = 0f
        private set
    var shadowLayerColor: Int = 0
        private set

    init {
        this.flags = flags or HIDDEN_DEFAULT_FLAGS
    }

    /** Drop shadow drawn under content. */
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        if (radius <= 0f) return clearShadowLayer()
        if (radius == shadowLayerRadius && dx == shadowLayerDx && dy == shadowLayerDy && shadowColor == shadowLayerColor) return
        shadowLayerRadius = radius
        shadowLayerDx = dx
        shadowLayerDy = dy
        shadowLayerColor = shadowColor
        val sigma = blurSigma(radius)
        skia.imageFilter = ImageFilter.makeDropShadow(dx, dy, sigma, sigma, shadowColor)
    }

    fun clearShadowLayer() {
        if (shadowLayerRadius == 0f) return
        shadowLayerRadius = 0f
        shadowLayerDx = 0f
        shadowLayerDy = 0f
        shadowLayerColor = 0
        skia.imageFilter = null
    }

    fun reset() {
        set(DEFAULTS)
    }

    fun set(src: Paint) {
        if (src === this) return
        flags = src.flags
        color = src.color
        style = src.style
        strokeWidth = src.strokeWidth
        strokeMiter = src.strokeMiter
        strokeCap = src.strokeCap
        strokeJoin = src.strokeJoin
        shader = src.shader
        colorFilter = src.colorFilter
        maskFilter = src.maskFilter
        pathEffect = src.pathEffect
        if (src.xfermode != null) xfermode = src.xfermode else blendMode = src.blendMode
        typeface = src.typeface
        textSize = src.textSize
        textScaleX = src.textScaleX
        textSkewX = src.textSkewX
        letterSpacing = src.letterSpacing
        textAlign = src.textAlign
        if (src.shadowLayerRadius > 0f) {
            setShadowLayer(src.shadowLayerRadius, src.shadowLayerDx, src.shadowLayerDy, src.shadowLayerColor)
        } else {
            clearShadowLayer()
        }
    }

    // Android ceils measureText
    fun measureText(text: String): Float {
        if (text.isEmpty()) return 0f
        layout(text)
        return ceil(runWidth)
    }

    fun measureText(text: String, start: Int, end: Int): Float = measureText(text.substring(start, end))

    fun measureText(text: CharSequence, start: Int, end: Int): Float = measureText(text.subSequence(start, end).toString())

    fun measureText(text: CharArray, index: Int, count: Int): Float = measureText(String(text, index, count))

    fun breakText(text: String, measureForwards: Boolean, maxWidth: Float, measuredWidth: FloatArray?): Int {
        val count = text.length
        if (count == 0) {
            if (measuredWidth != null && measuredWidth.isNotEmpty()) measuredWidth[0] = 0f
            return 0
        }
        val advances = charAdvances(text)
        var measuredCount = 0
        var measured = 0f
        for (i in 0 until count) {
            val index = if (measureForwards) i else count - i - 1
            val width = advances[index]
            if (measured + width > maxWidth) break
            measuredCount = if (measureForwards) i + 1 else count - index
            measured += width
        }
        if (measuredWidth != null && measuredWidth.isNotEmpty()) measuredWidth[0] = measured
        return measuredCount
    }

    fun breakText(text: CharSequence, start: Int, end: Int, measureForwards: Boolean, maxWidth: Float, measuredWidth: FloatArray?): Int =
        breakText(text.subSequence(start, end).toString(), measureForwards, maxWidth, measuredWidth)

    fun breakText(text: CharArray, index: Int, count: Int, maxWidth: Float, measuredWidth: FloatArray?): Int =
        breakText(String(text, index, if (count < 0) -count else count), count >= 0, maxWidth, measuredWidth)

    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val ink = font().measureText(text.substring(start, end), skia)
        bounds.set(floor(ink.left).toInt(), floor(ink.top).toInt(), ceil(ink.right).toInt(), ceil(ink.bottom).toInt())
    }

    fun getTextBounds(text: CharSequence, start: Int, end: Int, bounds: Rect) = getTextBounds(text.toString(), start, end, bounds)

    fun ascent(): Float = font().metrics.ascent

    fun descent(): Float = font().metrics.descent

    val fontSpacing: Float get() = getFontMetrics(null)

    val fontMetrics: FontMetrics get() = FontMetrics().also { getFontMetrics(it) }

    /** Returns recommended line spacing. */
    fun getFontMetrics(metrics: FontMetrics?): Float {
        val m = font().metrics
        if (metrics != null) {
            metrics.top = m.top
            metrics.ascent = m.ascent
            metrics.descent = m.descent
            metrics.bottom = m.bottom
            metrics.leading = m.leading
        }
        return m.descent - m.ascent + m.leading
    }

    private fun setFlag(flag: Int, on: Boolean) {
        flags = if (on) flags or flag else flags and flag.inv()
    }

    // Text layout, single-entry cache
    private var fontCache: Font? = null
    private var fontDirty = true
    private var stamp = 0
    private var runText: String? = null
    private var runStamp = -1
    private var runGlyphs = EMPTY_GLYPHS
    private var runPositions = EMPTY_FLOATS
    private var runAdvances = EMPTY_FLOATS
    private var runBlob: TextBlob? = null

    /** Advance incl. letter spacing. */
    internal var runWidth = 0f
        private set

    internal fun font(): Font {
        val font = fontCache ?: Font().also { fontCache = it }
        if (fontDirty) {
            fontDirty = false
            stamp++
            val face = typeface ?: Typeface.DEFAULT
            val wantBold = isFakeBoldText || ((face.style and Typeface.BOLD) != 0 && !face.skia.isBold)
            val fakeItalic = (face.style and Typeface.ITALIC) != 0 && !face.skia.isItalic
            font.setTypeface(face.skia)
            font.size = textSize
            font.scaleX = textScaleX
            font.skewX = textSkewX + if (fakeItalic) FAKE_ITALIC_SKEW else 0f
            font.isEmboldened = wantBold
            font.edging = if (isAntiAlias) FontEdging.ANTI_ALIAS else FontEdging.ALIAS
            font.isSubpixel = true
        }
        return font
    }

    // Half spacing before, half after
    internal fun layout(text: String) {
        val font = font()
        if (runStamp == stamp && text == runText) return
        runBlob?.close()
        runBlob = null
        val glyphs = if (text.isEmpty()) EMPTY_GLYPHS else font.getStringGlyphs(text)
        val widths = if (glyphs.isEmpty()) EMPTY_FLOATS else font.getWidths(glyphs)
        val spacing = letterSpacing * textSize * textScaleX
        val positions = FloatArray(glyphs.size)
        val advances = FloatArray(glyphs.size)
        var x = spacing / 2
        var total = 0f
        for (i in glyphs.indices) {
            val advance = widths[i] + spacing
            positions[i] = x
            advances[i] = advance
            x += advance
            total += advance
        }
        runText = text
        runStamp = stamp
        runGlyphs = glyphs
        runPositions = positions
        runAdvances = advances
        runWidth = total
    }

    /** Blob for last layout() text. */
    internal fun textBlob(): TextBlob? {
        if (runGlyphs.isEmpty()) return null
        runBlob?.let { return it }
        return TextBlob.makeFromPosH(runGlyphs, runPositions, 0f, font()).also { runBlob = it }
    }

    // Glyph advances spread per char
    private fun charAdvances(text: String): FloatArray {
        layout(text)
        val result = FloatArray(text.length)
        var i = 0
        var glyph = 0
        while (i < text.length) {
            val size = Character.charCount(text.codePointAt(i))
            if (glyph < runAdvances.size) result[i] = runAdvances[glyph]
            i += size
            glyph++
        }
        return result
    }

    companion object {
        const val ANTI_ALIAS_FLAG = 0x01
        const val FILTER_BITMAP_FLAG = 0x02
        const val DITHER_FLAG = 0x04
        const val UNDERLINE_TEXT_FLAG = 0x08
        const val STRIKE_THRU_TEXT_FLAG = 0x10
        const val FAKE_BOLD_TEXT_FLAG = 0x20
        const val LINEAR_TEXT_FLAG = 0x40
        const val SUBPIXEL_TEXT_FLAG = 0x80
        const val DEV_KERN_TEXT_FLAG = 0x100
        const val EMBEDDED_BITMAP_TEXT_FLAG = 0x400

        const val HINTING_OFF = 0x0
        const val HINTING_ON = 0x1

        // Android Q+ new-Paint defaults
        internal const val HIDDEN_DEFAULT_FLAGS = DEV_KERN_TEXT_FLAG or EMBEDDED_BITMAP_TEXT_FLAG or FILTER_BITMAP_FLAG

        private const val DEFAULT_TEXT_SIZE = 12f
        private const val DEFAULT_MITER = 4f
        private const val FAKE_ITALIC_SKEW = -0.25f
        private val EMPTY_GLYPHS = ShortArray(0)
        private val EMPTY_FLOATS = FloatArray(0)
        private val DEFAULTS by lazy { Paint() }

        /** Android radius to Skia sigma. */
        internal fun blurSigma(radius: Float): Float = if (radius > 0f) radius * 0.57735f + 0.5f else 0f
    }
}
