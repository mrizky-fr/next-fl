package android.graphics

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.skia.Rect as SkRect

/** Integer rectangle, Android semantics. */
class Rect() {
    @JvmField var left: Int = 0
    @JvmField var top: Int = 0
    @JvmField var right: Int = 0
    @JvmField var bottom: Int = 0

    constructor(left: Int, top: Int, right: Int, bottom: Int) : this() {
        set(left, top, right, bottom)
    }

    constructor(r: Rect?) : this() {
        if (r != null) set(r)
    }

    @get:JvmName("isEmpty")
    val isEmpty: Boolean get() = left >= right || top >= bottom

    fun width(): Int = right - left

    fun height(): Int = bottom - top

    fun centerX(): Int = (left + right) shr 1

    fun centerY(): Int = (top + bottom) shr 1

    fun exactCenterX(): Float = (left + right) * 0.5f

    fun exactCenterY(): Float = (top + bottom) * 0.5f

    fun setEmpty() = set(0, 0, 0, 0)

    fun set(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun set(src: Rect) = set(src.left, src.top, src.right, src.bottom)

    fun offset(dx: Int, dy: Int) {
        left += dx
        top += dy
        right += dx
        bottom += dy
    }

    fun offsetTo(newLeft: Int, newTop: Int) = offset(newLeft - left, newTop - top)

    fun inset(dx: Int, dy: Int) {
        left += dx
        top += dy
        right -= dx
        bottom -= dy
    }

    fun contains(x: Int, y: Int): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom

    fun intersect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (this.left < right && left < this.right && this.top < bottom && top < this.bottom) {
            if (this.left < left) this.left = left
            if (this.top < top) this.top = top
            if (this.right > right) this.right = right
            if (this.bottom > bottom) this.bottom = bottom
            return true
        }
        return false
    }

    fun intersect(r: Rect): Boolean = intersect(r.left, r.top, r.right, r.bottom)

    fun union(left: Int, top: Int, right: Int, bottom: Int) {
        if (left >= right || top >= bottom) return
        if (isEmpty) return set(left, top, right, bottom)
        if (this.left > left) this.left = left
        if (this.top > top) this.top = top
        if (this.right < right) this.right = right
        if (this.bottom < bottom) this.bottom = bottom
    }

    fun union(r: Rect) = union(r.left, r.top, r.right, r.bottom)

    fun sort() {
        if (left > right) left = right.also { right = left }
        if (top > bottom) top = bottom.also { bottom = top }
    }

    override fun equals(other: Any?): Boolean =
        other is Rect && left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = ((left * 31 + top) * 31 + right) * 31 + bottom

    override fun toString(): String = "Rect($left, $top - $right, $bottom)"
}

/** Float rectangle, Android semantics. */
class RectF() {
    @JvmField var left: Float = 0f
    @JvmField var top: Float = 0f
    @JvmField var right: Float = 0f
    @JvmField var bottom: Float = 0f

    constructor(left: Float, top: Float, right: Float, bottom: Float) : this() {
        set(left, top, right, bottom)
    }

    constructor(r: RectF?) : this() {
        if (r != null) set(r)
    }

    constructor(r: Rect?) : this() {
        if (r != null) set(r)
    }

    @get:JvmName("isEmpty")
    val isEmpty: Boolean get() = !(left < right && top < bottom)

    fun width(): Float = right - left

    fun height(): Float = bottom - top

    fun centerX(): Float = (left + right) * 0.5f

    fun centerY(): Float = (top + bottom) * 0.5f

    fun setEmpty() = set(0f, 0f, 0f, 0f)

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    fun set(src: RectF) = set(src.left, src.top, src.right, src.bottom)

    fun set(src: Rect) = set(src.left.toFloat(), src.top.toFloat(), src.right.toFloat(), src.bottom.toFloat())

    fun offset(dx: Float, dy: Float) {
        left += dx
        top += dy
        right += dx
        bottom += dy
    }

    fun offsetTo(newLeft: Float, newTop: Float) = offset(newLeft - left, newTop - top)

    fun inset(dx: Float, dy: Float) {
        left += dx
        top += dy
        right -= dx
        bottom -= dy
    }

    fun contains(x: Float, y: Float): Boolean = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom

    fun intersect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (this.left < right && left < this.right && this.top < bottom && top < this.bottom) {
            if (this.left < left) this.left = left
            if (this.top < top) this.top = top
            if (this.right > right) this.right = right
            if (this.bottom > bottom) this.bottom = bottom
            return true
        }
        return false
    }

    fun intersect(r: RectF): Boolean = intersect(r.left, r.top, r.right, r.bottom)

    fun union(left: Float, top: Float, right: Float, bottom: Float) {
        if (left >= right || top >= bottom) return
        if (this.left < this.right && this.top < this.bottom) {
            if (this.left > left) this.left = left
            if (this.top > top) this.top = top
            if (this.right < right) this.right = right
            if (this.bottom < bottom) this.bottom = bottom
        } else {
            set(left, top, right, bottom)
        }
    }

    fun union(r: RectF) = union(r.left, r.top, r.right, r.bottom)

    fun sort() {
        if (left > right) left = right.also { right = left }
        if (top > bottom) top = bottom.also { bottom = top }
    }

    fun round(dst: Rect) = dst.set(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

    fun roundOut(dst: Rect) = dst.set(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())

    override fun equals(other: Any?): Boolean =
        other is RectF && left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = ((left.hashCode() * 31 + top.hashCode()) * 31 + right.hashCode()) * 31 + bottom.hashCode()

    override fun toString(): String = "RectF($left, $top, $right, $bottom)"
}

internal fun RectF.toSkia(): SkRect = SkRect.makeLTRB(left, top, right, bottom)

internal fun Rect.toSkia(): SkRect = SkRect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
