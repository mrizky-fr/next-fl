package androidx.core.graphics

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/** androidx.core Canvas helpers. */
inline fun Canvas.withSave(block: Canvas.() -> Unit) {
    val checkpoint = save()
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withTranslation(x: Float = 0.0f, y: Float = 0.0f, block: Canvas.() -> Unit) {
    val checkpoint = save()
    translate(x, y)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withRotation(degrees: Float = 0.0f, pivotX: Float = 0.0f, pivotY: Float = 0.0f, block: Canvas.() -> Unit) {
    val checkpoint = save()
    rotate(degrees, pivotX, pivotY)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withScale(x: Float = 1.0f, y: Float = 1.0f, pivotX: Float = 0.0f, pivotY: Float = 0.0f, block: Canvas.() -> Unit) {
    val checkpoint = save()
    scale(x, y, pivotX, pivotY)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withSkew(x: Float = 0.0f, y: Float = 0.0f, block: Canvas.() -> Unit) {
    val checkpoint = save()
    skew(x, y)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withClip(clipRect: RectF, block: Canvas.() -> Unit) {
    val checkpoint = save()
    clipRect(clipRect)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withClip(clipRect: Rect, block: Canvas.() -> Unit) {
    val checkpoint = save()
    clipRect(clipRect)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}

inline fun Canvas.withClip(clipPath: Path, block: Canvas.() -> Unit) {
    val checkpoint = save()
    clipPath(clipPath)
    try {
        block()
    } finally {
        restoreToCount(checkpoint)
    }
}
