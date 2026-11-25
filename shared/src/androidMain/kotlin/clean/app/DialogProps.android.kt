package clean.app

import androidx.compose.ui.window.DialogProperties

actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
