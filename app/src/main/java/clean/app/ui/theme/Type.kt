package clean.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The text size the app starts with: the Material default taken down by this much. */
const val DEFAULT_TEXT_SCALE = 0.88f

/** Every text size is the Material default times [scale] (the small roles never under 11 sp), set in [family] when the user has imported a font. */
fun typography(scale: Float, family: FontFamily?): Typography {
    // Digits all take the same width, so a clock or a column of sets does not shift as the numbers change.
    fun TextStyle.fit(floor: Boolean = false): TextStyle {
        val size = if (floor) maxOf(fontSize.value * scale, 11f) else fontSize.value * scale
        return copy(fontSize = size.sp, lineHeight = lineHeight * (size / fontSize.value), fontFamily = family ?: fontFamily, fontFeatureSettings = "tnum")
    }
    // B1: titles sit a little tighter: letter spacing about -2 %, line height about 110 %.
    fun TextStyle.tight(): TextStyle = copy(letterSpacing = (-0.02).em, lineHeight = fontSize * 1.1f)
    return Typography().run {
        copy(
            displayLarge = displayLarge.fit(), displayMedium = displayMedium.fit(), displaySmall = displaySmall.fit(),
            headlineLarge = headlineLarge.fit().tight(), headlineMedium = headlineMedium.fit().tight(), headlineSmall = headlineSmall.fit().tight(),
            titleLarge = titleLarge.fit().tight(), titleMedium = titleMedium.fit(), titleSmall = titleSmall.fit(),
            bodyLarge = bodyLarge.fit(), bodyMedium = bodyMedium.fit(), bodySmall = bodySmall.fit(floor = true),
            labelLarge = labelLarge.fit(), labelMedium = labelMedium.fit(floor = true), labelSmall = labelSmall.fit(floor = true),
        )
    }
}
