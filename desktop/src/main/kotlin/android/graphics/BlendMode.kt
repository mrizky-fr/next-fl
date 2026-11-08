package android.graphics

import org.jetbrains.skia.BlendMode as SkBlendMode

/** Android BlendMode, 1:1 with Skia. */
enum class BlendMode(internal val skia: SkBlendMode) {
    CLEAR(SkBlendMode.CLEAR),
    SRC(SkBlendMode.SRC),
    DST(SkBlendMode.DST),
    SRC_OVER(SkBlendMode.SRC_OVER),
    DST_OVER(SkBlendMode.DST_OVER),
    SRC_IN(SkBlendMode.SRC_IN),
    DST_IN(SkBlendMode.DST_IN),
    SRC_OUT(SkBlendMode.SRC_OUT),
    DST_OUT(SkBlendMode.DST_OUT),
    SRC_ATOP(SkBlendMode.SRC_ATOP),
    DST_ATOP(SkBlendMode.DST_ATOP),
    XOR(SkBlendMode.XOR),
    PLUS(SkBlendMode.PLUS),
    MODULATE(SkBlendMode.MODULATE),
    SCREEN(SkBlendMode.SCREEN),
    OVERLAY(SkBlendMode.OVERLAY),
    DARKEN(SkBlendMode.DARKEN),
    LIGHTEN(SkBlendMode.LIGHTEN),
    COLOR_DODGE(SkBlendMode.COLOR_DODGE),
    COLOR_BURN(SkBlendMode.COLOR_BURN),
    HARD_LIGHT(SkBlendMode.HARD_LIGHT),
    SOFT_LIGHT(SkBlendMode.SOFT_LIGHT),
    DIFFERENCE(SkBlendMode.DIFFERENCE),
    EXCLUSION(SkBlendMode.EXCLUSION),
    MULTIPLY(SkBlendMode.MULTIPLY),
    HUE(SkBlendMode.HUE),
    SATURATION(SkBlendMode.SATURATION),
    COLOR(SkBlendMode.COLOR),
    LUMINOSITY(SkBlendMode.LUMINOSITY),
}

class PorterDuff {
    /** Android MULTIPLY is Skia modulate. */
    enum class Mode(internal val blend: BlendMode) {
        CLEAR(BlendMode.CLEAR),
        SRC(BlendMode.SRC),
        DST(BlendMode.DST),
        SRC_OVER(BlendMode.SRC_OVER),
        DST_OVER(BlendMode.DST_OVER),
        SRC_IN(BlendMode.SRC_IN),
        DST_IN(BlendMode.DST_IN),
        SRC_OUT(BlendMode.SRC_OUT),
        DST_OUT(BlendMode.DST_OUT),
        SRC_ATOP(BlendMode.SRC_ATOP),
        DST_ATOP(BlendMode.DST_ATOP),
        XOR(BlendMode.XOR),
        DARKEN(BlendMode.DARKEN),
        LIGHTEN(BlendMode.LIGHTEN),
        MULTIPLY(BlendMode.MODULATE),
        SCREEN(BlendMode.SCREEN),
        ADD(BlendMode.PLUS),
        OVERLAY(BlendMode.OVERLAY),
    }
}

open class Xfermode {
    internal open val blend: BlendMode get() = BlendMode.SRC_OVER
}

class PorterDuffXfermode(mode: PorterDuff.Mode) : Xfermode() {
    override val blend: BlendMode = mode.blend
}
