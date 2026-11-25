package clean.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import clean.app.platform.LocalPlatform
import clean.app.platform.Platform
import clean.app.ui.theme.CleanTheme
import clean.app.ui.theme.DEFAULT_TEXT_SCALE

/**
 * The whole app, from the theme down: what Android's `MainActivity` and the iPhone's `MainViewController` both put on screen.
 * [phoneStill] is the phone's own "animations off" setting; the platform's window work (system bars, launch screen) stays with each platform.
 */
@Composable
fun CleanRoot(model: AppModel, platform: Platform, phoneStill: Boolean) {
    val look by model.look.collectAsState()
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) { model.systemDark.value = systemDark }
    Motion.moving = model.flag("ui.motion") && !phoneStill
    val textSize by model.textSize.collectAsState()
    val scale = when (textSize) {
        "Smallest" -> 0.72f
        "Small" -> 0.8f
        "Large" -> 1f
        "Largest" -> 1.12f
        else -> DEFAULT_TEXT_SCALE
    }
    val performance by model.performance.collectAsState()
    // The theme cross-fade redraws the whole app twice, so Light has none.
    CleanTheme(look, scale, crossFade = model.loaded && model.flag("ui.motion") && !phoneStill && performance.flourishes) {
        // Glass blurs only on Android 12+, and not with Reduce transparency or the phone's own high-contrast text setting.
        // The performance tier is a floor: Balanced and Light draw glass opaque, and Reduce transparency or the phone's own setting can only add to that.
        val solidGlass = model.flag("glass.reduce", false) || performance.solidGlass || remember(platform) { platform.solidGlassForced }
        val animating = model.flag("ui.motion") && !phoneStill
        androidx.compose.runtime.SideEffect { MorphProbe.solidGlass = solidGlass; MorphProbe.performance = performance; MorphProbe.moving = animating }
        CompositionLocalProvider(LocalPlatform provides platform, LocalMotion provides (model.flag("ui.motion") && !phoneStill), LocalSolidGlass provides solidGlass, LocalPerformance provides performance, LocalHapticTicks provides model.flag("haptic.ticks")) { Surface(Modifier.fillMaxSize()) { App(model) } }
    }
}
