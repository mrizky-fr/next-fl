package id.nextfl.app.video

import id.nextfl.core.model.AspectRatio

/** Even frame sizes per aspect. */
object VideoFrames {
    fun frameSize(aspect: AspectRatio, short: Int): Pair<Int, Int> {
        val long = even(short * maxOf(aspect.width, aspect.height) / minOf(aspect.width, aspect.height))
        return if (aspect.width >= aspect.height) long to short else short to long
    }

    private fun even(value: Int) = value and 1.inv()
}
