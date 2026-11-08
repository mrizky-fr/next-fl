package android.graphics

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.io.File
import java.util.Locale
import org.jetbrains.skia.Typeface as SkTypeface

/** Android Typeface over a Skia typeface. */
class Typeface internal constructor(internal val skia: SkTypeface, val style: Int) {
    val isBold: Boolean get() = (style and BOLD) != 0

    val isItalic: Boolean get() = (style and ITALIC) != 0

    val weight: Int get() = skia.fontStyle.weight

    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        val DEFAULT: Typeface by lazy { defaultFromStyle(NORMAL) }
        val DEFAULT_BOLD: Typeface by lazy { defaultFromStyle(BOLD) }
        val SANS_SERIF: Typeface by lazy { create("sans-serif", NORMAL) }
        val SERIF: Typeface by lazy { create("serif", NORMAL) }
        val MONOSPACE: Typeface by lazy { create("monospace", NORMAL) }

        private val defaults = arrayOfNulls<Typeface>(4)

        @JvmStatic fun defaultFromStyle(style: Int): Typeface {
            val index = style and BOLD_ITALIC
            synchronized(defaults) {
                return defaults[index] ?: system(SANS_FAMILIES, index).also { defaults[index] = it }
            }
        }

        @JvmStatic fun create(familyName: String?, style: Int): Typeface {
            val families = when (familyName?.lowercase(Locale.ROOT)) {
                null, "", "sans-serif" -> return defaultFromStyle(style)
                "serif" -> SERIF_FAMILIES
                "monospace" -> MONO_FAMILIES
                else -> listOf(familyName)
            }
            return system(families, style and BOLD_ITALIC)
        }

        @JvmStatic fun create(family: Typeface?, style: Int): Typeface {
            if (family == null) return defaultFromStyle(style)
            val wanted = style and BOLD_ITALIC
            if (family.style == wanted) return family
            val name = family.skia.familyName
            val match = FontMgr.default.matchFamilyStyle(name, skiaStyle(wanted))
            // Paint synthesizes missing styles
            return Typeface(match ?: family.skia, wanted)
        }

        @JvmStatic fun createFromFile(path: String): Typeface {
            val face = FontMgr.default.makeFromFile(path) ?: throw RuntimeException("Font asset not found $path")
            return Typeface(face, styleOf(face))
        }

        @JvmStatic fun createFromFile(file: File): Typeface = createFromFile(file.absolutePath)

        private fun system(families: List<String>, style: Int): Typeface {
            val manager = FontMgr.default
            val fontStyle = skiaStyle(style)
            val face = families.firstNotNullOfOrNull { manager.matchFamilyStyle(it, fontStyle) }
                ?: manager.legacyMakeTypeface("", fontStyle)
                ?: SkTypeface.makeEmpty()
            return Typeface(face, style)
        }

        internal fun styleOf(face: SkTypeface): Int = (if (face.isBold) BOLD else 0) or (if (face.isItalic) ITALIC else 0)

        private fun skiaStyle(style: Int): FontStyle = when (style and BOLD_ITALIC) {
            BOLD -> FontStyle.BOLD
            ITALIC -> FontStyle.ITALIC
            BOLD_ITALIC -> FontStyle.BOLD_ITALIC
            else -> FontStyle.NORMAL
        }

        private val SANS_FAMILIES = listOf("Roboto", "Segoe UI", "Helvetica Neue", "Arial", "DejaVu Sans")
        private val SERIF_FAMILIES = listOf("Noto Serif", "Times New Roman", "Georgia", "DejaVu Serif")
        private val MONO_FAMILIES = listOf("Roboto Mono", "Consolas", "Menlo", "Courier New", "DejaVu Sans Mono")
    }
}

/** Desktop bridge: font file bytes. */
fun Typeface.Companion.fromBytes(bytes: ByteArray): Typeface {
    val face = Data.makeFromBytes(bytes).use { FontMgr.default.makeFromData(it) }
        ?: throw IllegalArgumentException("Unreadable font data")
    return Typeface(face, Typeface.styleOf(face))
}
