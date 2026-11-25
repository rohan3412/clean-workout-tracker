package clean.app

import androidx.compose.ui.Modifier

/**
 * Keeps the phone's own edge swipe (Back) off a strip of this element's [left] and [right] edge, so a swipe that starts there is the element's, not the
 * system's. Android takes a swipe from the screen's edge for Back and cancels the touch for the app; a set row is swiped from the very edge. The phone
 * limits what an app may exclude (200 dp of height per edge), so the strips are narrow and the system keeps the first ones it is given. iOS has no such claim.
 */
expect fun Modifier.keepSystemBackOff(left: Boolean, right: Boolean): Modifier
