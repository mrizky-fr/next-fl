package android.graphics

import org.jetbrains.skia.PathAddMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.PathOp
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Path as SkPath

/** Mutable Android Path over PathBuilder. */
open class Path() {
    private var builder = PathBuilder()
    private var snapshot: SkPath? = null
    private var fill = FillType.WINDING

    constructor(src: Path?) : this() {
        if (src != null) set(src)
    }

    enum class FillType(internal val skia: PathFillMode) {
        WINDING(PathFillMode.WINDING),
        EVEN_ODD(PathFillMode.EVEN_ODD),
        INVERSE_WINDING(PathFillMode.INVERSE_WINDING),
        INVERSE_EVEN_ODD(PathFillMode.INVERSE_EVEN_ODD),
    }

    enum class Direction(internal val skia: PathDirection) {
        CW(PathDirection.CLOCKWISE),
        CCW(PathDirection.COUNTER_CLOCKWISE),
    }

    enum class Op(internal val skia: PathOp) {
        DIFFERENCE(PathOp.DIFFERENCE),
        INTERSECT(PathOp.INTERSECT),
        UNION(PathOp.UNION),
        XOR(PathOp.XOR),
        REVERSE_DIFFERENCE(PathOp.REVERSE_DIFFERENCE),
    }

    /** Immutable view, rebuilt after edits. */
    internal val skia: SkPath
        get() = snapshot ?: builder.snapshot().also { snapshot = it }

    var fillType: FillType
        get() = fill
        set(value) {
            fill = value
            builder.setFillType(value.skia)
            changed()
        }

    val isInverseFillType: Boolean get() = fill.skia.isInverse

    fun toggleInverseFillType() {
        fillType = when (fill) {
            FillType.WINDING -> FillType.INVERSE_WINDING
            FillType.EVEN_ODD -> FillType.INVERSE_EVEN_ODD
            FillType.INVERSE_WINDING -> FillType.WINDING
            FillType.INVERSE_EVEN_ODD -> FillType.EVEN_ODD
        }
    }

    @get:JvmName("isEmpty")
    val isEmpty: Boolean get() = skia.isEmpty

    @get:JvmName("isConvex")
    val isConvex: Boolean get() = skia.isConvex

    /** Keeps fill type, like Android. */
    fun reset() {
        builder.reset()
        builder.setFillType(fill.skia)
        changed()
    }

    fun rewind() = reset()

    fun set(src: Path) {
        if (src === this) return
        replace(PathBuilder(src.skia), src.fill)
    }

    fun incReserve(extraPtCount: Int) {
        builder.incReserve(extraPtCount)
    }

    fun moveTo(x: Float, y: Float) = edit { moveTo(x, y) }

    fun rMoveTo(dx: Float, dy: Float) = edit { rMoveTo(dx, dy) }

    fun lineTo(x: Float, y: Float) = edit { lineTo(x, y) }

    fun rLineTo(dx: Float, dy: Float) = edit { rLineTo(dx, dy) }

    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = edit { quadTo(x1, y1, x2, y2) }

    fun rQuadTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) = edit { rQuadTo(dx1, dy1, dx2, dy2) }

    fun conicTo(x1: Float, y1: Float, x2: Float, y2: Float, weight: Float) = edit { conicTo(x1, y1, x2, y2, weight) }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = edit { cubicTo(x1, y1, x2, y2, x3, y3) }

    fun rCubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = edit { rCubicTo(x1, y1, x2, y2, x3, y3) }

    fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean) =
        arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, forceMoveTo)

    fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float) = arcTo(oval, startAngle, sweepAngle, false)

    fun arcTo(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean) =
        edit { arcTo(left, top, right, bottom, startAngle, sweepAngle, forceMoveTo) }

    fun close() = edit { closePath() }

    fun addRect(rect: RectF, dir: Direction) = addRect(rect.left, rect.top, rect.right, rect.bottom, dir)

    fun addRect(left: Float, top: Float, right: Float, bottom: Float, dir: Direction) =
        edit { addRect(left, top, right, bottom, dir.skia, 0) }

    fun addOval(oval: RectF, dir: Direction) = addOval(oval.left, oval.top, oval.right, oval.bottom, dir)

    fun addOval(left: Float, top: Float, right: Float, bottom: Float, dir: Direction) =
        edit { addOval(left, top, right, bottom, dir.skia, 1) }

    fun addCircle(x: Float, y: Float, radius: Float, dir: Direction) = edit { addCircle(x, y, radius, dir.skia) }

    fun addArc(oval: RectF, startAngle: Float, sweepAngle: Float) =
        addArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle)

    fun addArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) =
        edit { addArc(left, top, right, bottom, startAngle, sweepAngle) }

    fun addRoundRect(rect: RectF, rx: Float, ry: Float, dir: Direction) =
        addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, dir)

    // Zero radius draws rect
    fun addRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, dir: Direction) {
        if (rx <= 0f || ry <= 0f) return addRect(left, top, right, bottom, dir)
        edit { addRRect(RRect.makeLTRB(left, top, right, bottom, rx, ry), dir.skia, rrectStart(dir)) }
    }

    fun addRoundRect(rect: RectF, radii: FloatArray, dir: Direction) =
        addRoundRect(rect.left, rect.top, rect.right, rect.bottom, radii, dir)

    fun addRoundRect(left: Float, top: Float, right: Float, bottom: Float, radii: FloatArray, dir: Direction) {
        if (radii.size < 8) throw ArrayIndexOutOfBoundsException("radii[] needs 8 values")
        edit { addRRect(RRect.makeComplexLTRB(left, top, right, bottom, radii.copyOf(8)), dir.skia, rrectStart(dir)) }
    }

    fun addPath(src: Path) {
        val other = src.skia
        edit { addPath(other, PathAddMode.APPEND) }
    }

    fun addPath(src: Path, dx: Float, dy: Float) {
        val other = src.skia
        edit { addPath(other, dx, dy, PathAddMode.APPEND) }
    }

    fun offset(dx: Float, dy: Float) = edit { offset(dx, dy) }

    fun offset(dx: Float, dy: Float, dst: Path?) {
        if (dst == null) return offset(dx, dy)
        dst.set(this)
        dst.offset(dx, dy)
    }

    fun setLastPoint(dx: Float, dy: Float) = edit { setLastPt(dx, dy) }

    /** this = this op path. */
    fun op(path: Path, op: Op): Boolean = op(this, path, op)

    fun op(path1: Path, path2: Path, op: Op): Boolean {
        val result = SkPath.makeCombining(path1.skia, path2.skia, op.skia) ?: return false
        val type = FillType.entries.first { it.skia == result.fillMode }
        replace(PathBuilder(result), type)
        snapshot = result
        return true
    }

    fun computeBounds(bounds: RectF, exact: Boolean) {
        val box = if (exact) skia.computeTightBounds() else skia.bounds
        bounds.set(box.left, box.top, box.right, box.bottom)
    }

    private inline fun edit(block: PathBuilder.() -> Unit) {
        builder.block()
        changed()
    }

    // Old snapshot freed eagerly
    private fun changed() {
        snapshot?.close()
        snapshot = null
    }

    private fun replace(next: PathBuilder, type: FillType) {
        builder.close()
        builder = next
        fill = type
        builder.setFillType(type.skia)
        changed()
    }

    private fun rrectStart(dir: Direction) = if (dir == Direction.CW) 6 else 7
}
