package android.graphics

import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Shader as SkShader

/** Android Shader base. */
open class Shader {
    internal open val skia: SkShader? get() = null

    enum class TileMode(internal val skia: FilterTileMode) {
        CLAMP(FilterTileMode.CLAMP),
        REPEAT(FilterTileMode.REPEAT),
        MIRROR(FilterTileMode.MIRROR),
        DECAL(FilterTileMode.DECAL),
    }

    internal companion object {
        // Android interpolates in premul
        private val INTERPOLATION = Gradient.Interpolation(
            Gradient.Interpolation.InPremul.YES,
            Gradient.Interpolation.ColorSpace.DESTINATION,
            Gradient.Interpolation.HueMethod.SHORTER,
        )

        fun gradient(colors: IntArray, positions: FloatArray?, tile: TileMode): Gradient {
            require(colors.size >= 2) { "needs >= 2 number of colors" }
            require(positions == null || positions.size == colors.size) { "color and position arrays must be of equal length" }
            val stops = Array(colors.size) { Color4f(colors[it]) }
            return Gradient(Gradient.Colors(stops, positions, tile.skia, null), INTERPOLATION)
        }
    }
}

class LinearGradient private constructor(override val skia: SkShader) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?, tile: TileMode) :
        this(SkShader.makeLinearGradient(x0, y0, x1, y1, gradient(colors, positions, tile)))

    constructor(x0: Float, y0: Float, x1: Float, y1: Float, color0: Int, color1: Int, tile: TileMode) :
        this(x0, y0, x1, y1, intArrayOf(color0, color1), null, tile)
}

class RadialGradient private constructor(override val skia: SkShader) : Shader() {
    constructor(centerX: Float, centerY: Float, radius: Float, colors: IntArray, stops: FloatArray?, tileMode: TileMode) :
        this(SkShader.makeRadialGradient(centerX, centerY, checkRadius(radius), gradient(colors, stops, tileMode)))

    constructor(centerX: Float, centerY: Float, radius: Float, centerColor: Int, edgeColor: Int, tileMode: TileMode) :
        this(centerX, centerY, radius, intArrayOf(centerColor, edgeColor), null, tileMode)

    private companion object {
        fun checkRadius(radius: Float): Float {
            require(radius > 0f) { "radius must be > 0" }
            return radius
        }
    }
}

class SweepGradient private constructor(override val skia: SkShader) : Shader() {
    constructor(cx: Float, cy: Float, colors: IntArray, positions: FloatArray?) :
        this(SkShader.makeSweepGradient(cx, cy, gradient(colors, positions, TileMode.CLAMP)))

    constructor(cx: Float, cy: Float, color0: Int, color1: Int) : this(cx, cy, intArrayOf(color0, color1), null)
}
