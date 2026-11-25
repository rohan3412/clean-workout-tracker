package clean.app

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity

/** The width of the strip kept from the system, from the element's edge inward. */
private const val STRIP_DP = 36

actual fun Modifier.keepSystemBackOff(left: Boolean, right: Boolean): Modifier = composed {
    val strip = STRIP_DP * LocalDensity.current.density
    this
        .then(if (left) Modifier.systemGestureExclusion { c: androidx.compose.ui.layout.LayoutCoordinates -> Rect(0f, 0f, strip, c.size.height.toFloat()) } else Modifier)
        .then(if (right) Modifier.systemGestureExclusion { c: androidx.compose.ui.layout.LayoutCoordinates -> Rect(c.size.width - strip, 0f, c.size.width.toFloat(), c.size.height.toFloat()) } else Modifier)
}
