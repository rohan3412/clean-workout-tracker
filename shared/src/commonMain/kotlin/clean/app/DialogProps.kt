package clean.app

import androidx.compose.ui.window.DialogProperties

/** The window of a page that opens from a card and covers the whole screen: no platform width, and on Android the content drawn under the bars. */
expect fun fullScreenDialogProperties(): DialogProperties
