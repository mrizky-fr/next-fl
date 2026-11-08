package android.graphics

import org.jetbrains.skia.ColorFilter as SkColorFilter
import org.jetbrains.skia.ColorMatrix as SkColorMatrix

/** Android ColorFilter base. */
open class ColorFilter {
    internal open val skia: SkColorFilter? get() = null
}

/** Android 4x5 matrix, offsets 0..255. */
class ColorMatrix() {
    val array = FloatArray(20)

    init {
        reset()
    }

    constructor(src: FloatArray) : this() {
        set(src)
    }

    constructor(src: ColorMatrix) : this() {
        set(src)
    }

    fun reset() {
        array.fill(0f)
        array[0] = 1f
        array[6] = 1f
        array[12] = 1f
        array[18] = 1f
    }

    fun set(src: ColorMatrix) = src.array.copyInto(array)

    fun set(src: FloatArray) = src.copyInto(array, 0, 0, 20)

    fun setScale(rScale: Float, gScale: Float, bScale: Float, aScale: Float) {
        array.fill(0f)
        array[0] = rScale
        array[6] = gScale
        array[12] = bScale
        array[18] = aScale
    }

    fun setRotate(axis: Int, degrees: Float) {
        reset()
        val radians = degrees * Math.PI / 180.0
        val cosine = Math.cos(radians).toFloat()
        val sine = Math.sin(radians).toFloat()
        when (axis) {
            0 -> {
                array[6] = cosine
                array[12] = cosine
                array[7] = sine
                array[11] = -sine
            }
            1 -> {
                array[0] = cosine
                array[12] = cosine
                array[2] = -sine
                array[10] = sine
            }
            2 -> {
                array[0] = cosine
                array[6] = cosine
                array[1] = sine
                array[5] = -sine
            }
            else -> throw RuntimeException()
        }
    }

    fun setConcat(matA: ColorMatrix, matB: ColorMatrix) {
        val tmp = if (matA === this || matB === this) FloatArray(20) else array
        val a = matA.array
        val b = matB.array
        var index = 0
        for (j in 0 until 20 step 5) {
            for (i in 0 until 4) {
                tmp[index++] = a[j] * b[i] + a[j + 1] * b[i + 5] + a[j + 2] * b[i + 10] + a[j + 3] * b[i + 15]
            }
            tmp[index++] = a[j] * b[4] + a[j + 1] * b[9] + a[j + 2] * b[14] + a[j + 3] * b[19] + a[j + 4]
        }
        if (tmp !== array) tmp.copyInto(array)
    }

    fun preConcat(prematrix: ColorMatrix) = setConcat(this, prematrix)

    fun postConcat(postmatrix: ColorMatrix) = setConcat(postmatrix, this)

    fun setSaturation(sat: Float) {
        reset()
        val invSat = 1 - sat
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat
        array[0] = r + sat
        array[1] = g
        array[2] = b
        array[5] = r
        array[6] = g + sat
        array[7] = b
        array[10] = r
        array[11] = g
        array[12] = b + sat
    }

    fun setRGB2YUV() {
        reset()
        array[0] = 0.299f
        array[1] = 0.587f
        array[2] = 0.114f
        array[5] = -0.16874f
        array[6] = -0.33126f
        array[7] = 0.5f
        array[10] = 0.5f
        array[11] = -0.41869f
        array[12] = -0.08131f
    }

    fun setYUV2RGB() {
        reset()
        array[2] = 1.402f
        array[5] = 1f
        array[6] = -0.34414f
        array[7] = -0.71414f
        array[10] = 1f
        array[11] = 1.772f
        array[12] = 0f
    }

    override fun equals(other: Any?): Boolean = other is ColorMatrix && other.array.contentEquals(array)

    override fun hashCode(): Int = array.contentHashCode()
}

/** Snapshot of matrix at creation. */
class ColorMatrixColorFilter(array: FloatArray) : ColorFilter() {
    constructor(matrix: ColorMatrix) : this(matrix.array)

    init {
        if (array.size < 20) throw ArrayIndexOutOfBoundsException()
    }

    private val values: FloatArray = array.copyOf(20)

    // Skia offsets are 0..1
    override val skia: SkColorFilter = SkColorFilter.makeMatrix(
        SkColorMatrix(FloatArray(20) { i -> if (i % 5 == 4) values[i] / 255f else values[i] }),
    )

    fun getColorMatrix(colorMatrix: ColorMatrix) = colorMatrix.set(values)
}

class PorterDuffColorFilter(val color: Int, val mode: PorterDuff.Mode) : ColorFilter() {
    override val skia: SkColorFilter = SkColorFilter.makeBlend(color, mode.blend.skia)
}

class BlendModeColorFilter(val color: Int, val mode: BlendMode) : ColorFilter() {
    override val skia: SkColorFilter = SkColorFilter.makeBlend(color, mode.skia)
}

class LightingColorFilter(val colorMultiply: Int, val colorAdd: Int) : ColorFilter() {
    override val skia: SkColorFilter = SkColorFilter.makeLighting(colorMultiply, colorAdd)
}
