package android.graphics

import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.VertexMode
import org.jetbrains.skia.BlendMode as SkBlendMode
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint

/** Android Canvas over a Skia canvas. */
open class Canvas private constructor(internal val skia: SkCanvas, private val target: Bitmap?) {
    /** Draws into bitmap pixels. */
    constructor(bitmap: Bitmap) : this(canvasFor(bitmap), bitmap)

    /** Desktop bridge: wraps a frame canvas. */
    constructor(skia: SkCanvas) : this(skia, null)

    private var scratchPaint: SkPaint? = null

    val saveCount: Int get() = skia.saveCount

    fun save(): Int = skia.save()

    fun saveLayer(bounds: RectF?, paint: Paint?): Int = skia.saveLayer(bounds?.toSkia(), paint?.skia)

    fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int = saveLayer(bounds, paint)

    fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int =
        skia.saveLayer(left, top, right, bottom, paint?.skia)

    fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
        val paint = scratch()
        paint.alpha = alpha
        return skia.saveLayer(bounds?.toSkia(), paint)
    }

    fun saveLayerAlpha(left: Float, top: Float, right: Float, bottom: Float, alpha: Int): Int {
        val paint = scratch()
        paint.alpha = alpha
        return skia.saveLayer(left, top, right, bottom, paint)
    }

    fun restore() {
        skia.restore()
        touch()
    }

    fun restoreToCount(saveCount: Int) {
        skia.restoreToCount(saveCount)
        touch()
    }

    fun translate(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        skia.translate(dx, dy)
    }

    fun scale(sx: Float, sy: Float) {
        if (sx == 1f && sy == 1f) return
        skia.scale(sx, sy)
    }

    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        if (sx == 1f && sy == 1f) return
        translate(px, py)
        skia.scale(sx, sy)
        translate(-px, -py)
    }

    /** Degrees, clockwise. */
    fun rotate(degrees: Float) {
        if (degrees == 0f) return
        skia.rotate(degrees)
    }

    fun rotate(degrees: Float, px: Float, py: Float) {
        if (degrees == 0f) return
        translate(px, py)
        skia.rotate(degrees)
        translate(-px, -py)
    }

    fun skew(sx: Float, sy: Float) {
        if (sx == 0f && sy == 0f) return
        skia.skew(sx, sy)
    }

    // Skiko hides clip bounds
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        skia.clipRect(left, top, right, bottom, ClipMode.INTERSECT, false)
        return true
    }

    fun clipRect(rect: RectF): Boolean = clipRect(rect.left, rect.top, rect.right, rect.bottom)

    fun clipRect(rect: Rect): Boolean = clipRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())

    fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        clipRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        skia.clipRect(left, top, right, bottom, ClipMode.DIFFERENCE, false)
        return true
    }

    fun clipOutRect(rect: RectF): Boolean = clipOutRect(rect.left, rect.top, rect.right, rect.bottom)

    // Android clips paths antialiased
    fun clipPath(path: Path): Boolean {
        skia.clipPath(path.skia, ClipMode.INTERSECT, true)
        return true
    }

    fun clipOutPath(path: Path): Boolean {
        skia.clipPath(path.skia, ClipMode.DIFFERENCE, true)
        return true
    }

    fun drawColor(color: Int) = drawColor(color, BlendMode.SRC_OVER)

    fun drawColor(color: Int, mode: PorterDuff.Mode) = drawColor(color, mode.blend)

    fun drawColor(color: Int, mode: BlendMode) {
        touch()
        val paint = scratch()
        paint.color = color
        paint.blendMode = mode.skia
        skia.drawPaint(paint)
    }

    fun drawARGB(a: Int, r: Int, g: Int, b: Int) = drawColor(Color.argb(a, r, g, b))

    fun drawRGB(r: Int, g: Int, b: Int) = drawColor(Color.rgb(r, g, b))

    fun drawPaint(paint: Paint) {
        touch()
        skia.drawPaint(paint.skia)
    }

    fun drawPoint(x: Float, y: Float, paint: Paint) {
        touch()
        skia.drawPoint(x, y, paint.skia)
    }

    fun drawPoints(pts: FloatArray, paint: Paint) = drawPoints(pts, 0, pts.size, paint)

    fun drawPoints(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
        val usable = count and 1.inv()
        if (usable <= 0) return
        touch()
        skia.drawPoints(slice(pts, offset, usable), paint.skia)
    }

    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        touch()
        skia.drawLine(startX, startY, stopX, stopY, paint.skia)
    }

    fun drawLines(pts: FloatArray, paint: Paint) = drawLines(pts, 0, pts.size, paint)

    /** Count is floats, 4 per line. */
    fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) {
        val usable = count and 3.inv()
        if (usable <= 0) return
        touch()
        skia.drawLines(slice(pts, offset, usable), paint.skia)
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        touch()
        skia.drawRect(left, top, right, bottom, paint.skia)
    }

    fun drawRect(rect: RectF, paint: Paint) = drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)

    fun drawRect(rect: Rect, paint: Paint) =
        drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        touch()
        if (rx <= 0f || ry <= 0f) {
            skia.drawRect(left, top, right, bottom, paint.skia)
        } else {
            skia.drawRRect(RRect.makeLTRB(left, top, right, bottom, rx, ry), paint.skia)
        }
    }

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) =
        drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint)

    fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        touch()
        skia.drawOval(left, top, right, bottom, paint.skia)
    }

    fun drawOval(oval: RectF, paint: Paint) = drawOval(oval.left, oval.top, oval.right, oval.bottom, paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        touch()
        skia.drawCircle(cx, cy, radius, paint.skia)
    }

    fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    ) {
        touch()
        skia.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint.skia)
    }

    fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint)

    fun drawPath(path: Path, paint: Paint) {
        touch()
        skia.drawPath(path.skia, paint.skia)
    }

    /** y is the baseline. */
    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        paint.layout(text)
        val blob = paint.textBlob() ?: return
        touch()
        val shift = when (paint.textAlign) {
            Paint.Align.LEFT -> 0f
            Paint.Align.CENTER -> -paint.runWidth / 2
            Paint.Align.RIGHT -> -paint.runWidth
        }
        skia.drawTextBlob(blob, x + shift, y, paint.skia)
    }

    fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) =
        drawText(text.substring(start, end), x, y, paint)

    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) =
        drawText(text.subSequence(start, end).toString(), x, y, paint)

    fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) =
        drawText(String(text, index, count), x, y, paint)

    fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        val image = bitmap.image()
        touch()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        skia.drawImageRect(image, 0f, 0f, w, h, left, top, left + w, top + h, sampling(paint), paint?.skia, false)
    }

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) =
        drawImage(bitmap, src, dst.left, dst.top, dst.right, dst.bottom, paint)

    fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) =
        drawImage(bitmap, src, dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat(), paint)

    /** Triangle mesh, bitmap as texture. */
    fun drawBitmapMesh(
        bitmap: Bitmap,
        meshWidth: Int,
        meshHeight: Int,
        verts: FloatArray,
        vertOffset: Int,
        colors: IntArray?,
        colorOffset: Int,
        paint: Paint?,
    ) {
        if (meshWidth < 0 || meshHeight < 0 || vertOffset < 0 || colorOffset < 0) throw ArrayIndexOutOfBoundsException()
        if (meshWidth == 0 || meshHeight == 0) return
        val count = (meshWidth + 1) * (meshHeight + 1)
        if (verts.size < vertOffset + count * 2) throw ArrayIndexOutOfBoundsException()
        if (colors != null && colors.size < colorOffset + count) throw ArrayIndexOutOfBoundsException()
        val shader = bitmap.shader(paint == null || paint.isFilterBitmap)
        touch()
        val mesh = Mesh.of(bitmap.width, bitmap.height, meshWidth, meshHeight)
        val positions = slice(verts, vertOffset, count * 2)
        val tint = colors?.let { if (colorOffset == 0 && it.size == count) it else it.copyOfRange(colorOffset, colorOffset + count) }
        val skPaint = paint?.skia ?: scratch()
        skPaint.shader = shader
        try {
            skia.drawVertices(VertexMode.TRIANGLES, positions, tint, mesh.texCoords, mesh.indices, SkBlendMode.MODULATE, skPaint)
        } finally {
            skPaint.shader = paint?.shader?.skia
        }
    }

    private fun drawImage(bitmap: Bitmap, src: Rect?, left: Float, top: Float, right: Float, bottom: Float, paint: Paint?) {
        val image = bitmap.image()
        touch()
        if (src == null) {
            skia.drawImageRect(
                image, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
                left, top, right, bottom, sampling(paint), paint?.skia, false,
            )
        } else {
            skia.drawImageRect(
                image, src.left.toFloat(), src.top.toFloat(), src.right.toFloat(), src.bottom.toFloat(),
                left, top, right, bottom, sampling(paint), paint?.skia, false,
            )
        }
    }

    // Null paint means default
    private fun sampling(paint: Paint?): SamplingMode =
        if (paint == null || paint.isFilterBitmap) Bitmap.LINEAR else Bitmap.NEAREST

    private fun scratch(): SkPaint {
        val paint = scratchPaint ?: SkPaint().also { scratchPaint = it }
        paint.reset()
        return paint
    }

    private fun touch() {
        target?.notifyPixelsChanged()
    }

    /** Android mesh texcoords and indices. */
    private class Mesh(
        val bitmapWidth: Int,
        val bitmapHeight: Int,
        val meshWidth: Int,
        val meshHeight: Int,
        val texCoords: FloatArray,
        val indices: ShortArray,
    ) {
        companion object {
            @Volatile private var last: Mesh? = null

            fun of(bitmapWidth: Int, bitmapHeight: Int, meshWidth: Int, meshHeight: Int): Mesh {
                val cached = last
                if (cached != null && cached.bitmapWidth == bitmapWidth && cached.bitmapHeight == bitmapHeight &&
                    cached.meshWidth == meshWidth && cached.meshHeight == meshHeight
                ) {
                    return cached
                }
                return build(bitmapWidth, bitmapHeight, meshWidth, meshHeight).also { last = it }
            }

            private fun build(bitmapWidth: Int, bitmapHeight: Int, meshWidth: Int, meshHeight: Int): Mesh {
                val w = bitmapWidth.toFloat()
                val h = bitmapHeight.toFloat()
                val dx = w / meshWidth
                val dy = h / meshHeight
                val texs = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)
                var t = 0
                var y = 0f
                for (i in 0..meshHeight) {
                    if (i == meshHeight) y = h
                    var x = 0f
                    repeat(meshWidth) {
                        texs[t++] = x
                        texs[t++] = y
                        x += dx
                    }
                    texs[t++] = w
                    texs[t++] = y
                    y += dy
                }
                val indices = ShortArray(meshWidth * meshHeight * 6)
                var k = 0
                var index = 0
                repeat(meshHeight) {
                    repeat(meshWidth) {
                        indices[k++] = index.toShort()
                        indices[k++] = (index + meshWidth + 1).toShort()
                        indices[k++] = (index + meshWidth + 2).toShort()
                        indices[k++] = index.toShort()
                        indices[k++] = (index + meshWidth + 2).toShort()
                        indices[k++] = (index + 1).toShort()
                        index++
                    }
                    index++
                }
                return Mesh(bitmapWidth, bitmapHeight, meshWidth, meshHeight, texs, indices)
            }
        }
    }

    private companion object {
        fun canvasFor(bitmap: Bitmap): SkCanvas {
            bitmap.checkAlive()
            check(bitmap.isMutable) { "Immutable bitmap passed to Canvas constructor" }
            return SkCanvas(bitmap.skia)
        }

        fun slice(values: FloatArray, offset: Int, count: Int): FloatArray =
            if (offset == 0 && count == values.size) values else values.copyOfRange(offset, offset + count)
    }
}
