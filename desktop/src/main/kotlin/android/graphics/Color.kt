package android.graphics

import java.util.Locale

/** Android Color statics on desktop. */
class Color {
    companion object {
        const val BLACK: Int = 0xFF000000.toInt()
        const val DKGRAY: Int = 0xFF444444.toInt()
        const val GRAY: Int = 0xFF888888.toInt()
        const val LTGRAY: Int = 0xFFCCCCCC.toInt()
        const val WHITE: Int = 0xFFFFFFFF.toInt()
        const val RED: Int = 0xFFFF0000.toInt()
        const val GREEN: Int = 0xFF00FF00.toInt()
        const val BLUE: Int = 0xFF0000FF.toInt()
        const val YELLOW: Int = 0xFFFFFF00.toInt()
        const val CYAN: Int = 0xFF00FFFF.toInt()
        const val MAGENTA: Int = 0xFFFF00FF.toInt()
        const val TRANSPARENT: Int = 0

        @JvmStatic fun alpha(color: Int): Int = color ushr 24

        @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF

        @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF

        @JvmStatic fun blue(color: Int): Int = color and 0xFF

        @JvmStatic fun rgb(red: Int, green: Int, blue: Int): Int = BLACK or (red shl 16) or (green shl 8) or blue

        @JvmStatic fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue

        @JvmStatic fun rgb(red: Float, green: Float, blue: Float): Int = argb(1f, red, green, blue)

        @JvmStatic fun argb(alpha: Float, red: Float, green: Float, blue: Float): Int =
            (unit(alpha) shl 24) or (unit(red) shl 16) or (unit(green) shl 8) or unit(blue)

        /** "#RRGGBB", "#AARRGGBB" or a name. */
        @JvmStatic fun parseColor(colorString: String): Int {
            if (colorString.startsWith('#')) {
                var color = colorString.substring(1).toLong(16)
                when (colorString.length) {
                    7 -> color = color or 0xFF000000
                    9 -> Unit
                    else -> throw IllegalArgumentException("Unknown color")
                }
                return color.toInt()
            }
            return NAMES[colorString.lowercase(Locale.ROOT)] ?: throw IllegalArgumentException("Unknown color")
        }

        private fun unit(value: Float): Int = (value * 255f + 0.5f).toInt()

        private val NAMES = mapOf(
            "black" to BLACK,
            "darkgray" to DKGRAY,
            "gray" to GRAY,
            "lightgray" to LTGRAY,
            "white" to WHITE,
            "red" to RED,
            "green" to GREEN,
            "blue" to BLUE,
            "yellow" to YELLOW,
            "cyan" to CYAN,
            "magenta" to MAGENTA,
            "aqua" to CYAN,
            "fuchsia" to MAGENTA,
            "darkgrey" to DKGRAY,
            "grey" to GRAY,
            "lightgrey" to LTGRAY,
            "lime" to GREEN,
            "maroon" to 0xFF800000.toInt(),
            "navy" to 0xFF000080.toInt(),
            "olive" to 0xFF808000.toInt(),
            "purple" to 0xFF800080.toInt(),
            "silver" to 0xFFC0C0C0.toInt(),
            "teal" to 0xFF008080.toInt(),
        )
    }
}
