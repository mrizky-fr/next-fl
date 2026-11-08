package android.graphics

import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter as SkMaskFilter
import org.jetbrains.skia.PathEffect as SkPathEffect

/** Android MaskFilter base. */
open class MaskFilter {
    internal open val skia: SkMaskFilter? get() = null
}

class BlurMaskFilter(radius: Float, style: Blur) : MaskFilter() {
    enum class Blur(internal val skia: FilterBlurMode) {
        NORMAL(FilterBlurMode.NORMAL),
        SOLID(FilterBlurMode.SOLID),
        OUTER(FilterBlurMode.OUTER),
        INNER(FilterBlurMode.INNER),
    }

    // Zero radius means no blur
    override val skia: SkMaskFilter? =
        if (radius > 0f) SkMaskFilter.makeBlur(style.skia, Paint.blurSigma(radius), true) else null
}

/** Android PathEffect base. */
open class PathEffect {
    internal open val skia: SkPathEffect? get() = null
}

class CornerPathEffect(radius: Float) : PathEffect() {
    override val skia: SkPathEffect? = if (radius > 0f) SkPathEffect.makeCorner(radius) else null
}

class DashPathEffect(intervals: FloatArray, phase: Float) : PathEffect() {
    init {
        require(intervals.size >= 2) { "intervals array must contain at least 2 values" }
    }

    override val skia: SkPathEffect? = SkPathEffect.makeDash(intervals.copyOf(intervals.size and 1.inv()), phase)
}
