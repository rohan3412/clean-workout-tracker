package clean.app

import androidx.compose.ui.unit.dp

/**
 * The steps of space between and around things: paddings, gaps in a row or column and spacers all take one of these, so the air
 * on every screen comes from one scale. Sizes of things (a box, a chart's height, the room kept for the tab bar) are not spacing
 * and keep their own numbers; a hairline of 1 dp or less is not a step either.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}
