package id.nextfl.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import id.nextfl.app.R

/** Inter for android.graphics canvases. */
object Fonts {
    fun semibold(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD

    fun bold(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
}
