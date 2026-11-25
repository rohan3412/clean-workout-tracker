package clean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import clean.app.ios.IosHost
import clean.app.platform.Platform
import platform.UIKit.UIViewController

/**
 * The iPhone's one screen: the same root the Android activity shows. [platform] is `IosHost.platform`. The model lives as long as the root;
 * after a restore or a wipe (which restart the process on Android) the host raises its generation and the root builds a new one. The page
 * stack is read from the file the host wrote when the app last went to the background, as Android's saved state is.
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun MainViewController(platform: Platform): UIViewController = ComposeUIViewController { IosRoot(platform) }

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
@Composable
private fun IosRoot(platform: Platform) {
    val generation = IosHost.generation.intValue
    val store = remember(generation) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val model = remember(store) {
        val saved = IosHost.newSavedState()
        ViewModelProvider.create(store, viewModelFactory { initializer { AppModel(platform, saved) } })[AppModel::class]
    }
    DisposableEffect(model) {
        IosHost.attach(model)
        onDispose { IosHost.detach(model) }
    }
    val theme by model.theme.collectAsState()
    val look by model.look.collectAsState()
    // The status bar's icons follow the app's theme, not the phone's (Android's enableEdgeToEdge in MainActivity).
    LaunchedEffect(theme, look.dark) { IosHost.applyAppearance(followSystem = theme == "phone", dark = look.dark) }
    val systemDark = IosHost.systemDark.value
    CompositionLocalProvider(LocalSystemTheme provides if (systemDark) SystemTheme.Dark else SystemTheme.Light) {
        // Nothing is drawn until the profile's data has been read, so the first picture is a filled page and not an empty one.
        if (model.loaded) CleanRoot(model, platform, IosHost.phoneStill.value)
        else Box(Modifier.fillMaxSize().background(if (systemDark) Color.Black else Color.White))
    }
}
