package clean.app

import clean.app.android.*

import clean.app.platform.LocalPlatform
import clean.app.platform.actionOfLink
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import clean.app.ui.theme.CleanTheme
import clean.app.ui.theme.DEFAULT_TEXT_SCALE
import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver

/**
 * What the app was opened to do, as an action for [AppModel.widgetAction]: from a widget or a
 * launcher shortcut (the `widget` extra), or from a link in another app (`clean://start`,
 * `clean://start?template=Push`, `clean://weight`, `clean://resume`, `clean://open?tab=history`, `tab=body`, `tab=home`, `tab=progress`, `tab=library` (or `exercises`), `tab=workout` for the Workout sheet and `tab=profile` for the Profile tab).
 */
private fun actionOf(intent: Intent): String? = intent.getStringExtra("widget")?.takeIf { it.isNotEmpty() }
    ?: intent.data?.takeIf { it.scheme == "clean" }?.let { link -> actionOfLink(link.host, link.getQueryParameter("template"), link.getQueryParameter("tab")) }

// A FragmentActivity (a ComponentActivity underneath) because BiometricPrompt shows its dialog as a fragment.
class MainActivity : androidx.fragment.app.FragmentActivity() {
    /** What a home-screen widget asked for when it opened the app, until the app has acted on it. */
    private var widgetAction by mutableStateOf<String?>(null)
    private var sharedFile by mutableStateOf<android.net.Uri?>(null)
    /** The phone's animations are off (animator scale 0); read again each time the app comes to the front. */
    private var phoneStill by mutableStateOf(false)

    /** The file an intent carries, when it is one: opened with Clean, or sent to it from a share sheet. */
    @Suppress("DEPRECATION")
    private fun fileOf(intent: Intent): android.net.Uri? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
        Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        widgetAction = actionOf(intent); sharedFile = fileOf(intent)
    }

    private val platform by lazy { AndroidPlatform(application) }
    private val model: AppModel by viewModels {
        viewModelFactory { initializer { AppModel(platform, createSavedStateHandle()) } }
    }

    // A rest that ends while the app is behind is carried by an alarm (see RestAlert); in front, the app counts it itself.
    override fun onDestroy() {
        if (platform.activity === this) platform.activity = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        phoneStill = platform.systemAnimationsOff
        model.onStarted()
    }

    override fun onStop() {
        super.onStop()
        model.onStopped()
        // Round 31: Lock photos locks again when the app leaves the screen (not for a turn of the phone).
        if (!isChangingConfigurations) model.lockPhotos()
        // Never while the app is in front or for a rotation: see AppIcon.
        if (!isChangingConfigurations) platform.applyAppIcon(model.look.value.themeName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform.activity = this
        // Nothing is drawn until the profile's data has been read (ten seconds at the very most), so the
        // launch screen gives way to a filled page and not to an empty one that fills a moment later.
        val content: View = findViewById(android.R.id.content)
        val started = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!model.loaded && SystemClock.uptimeMillis() - started < 10_000) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        // Only on a fresh start: after a rotation the same intent comes round again and must not act twice.
        if (savedInstanceState == null) {
            widgetAction = actionOf(intent)
            sharedFile = fileOf(intent)
        }
        setContent {
            // A templates file opened with Clean, or shared to it, goes to the Workout sheet's import.
            LaunchedEffect(sharedFile) {
                sharedFile?.let {
                    model.stack.clear()
                    model.openWorkoutSheet()
                    model.sharedFile = it.toString()
                    sharedFile = null
                }
            }
            LaunchedEffect(widgetAction) {
                widgetAction?.let {
                    model.widgetAction(it)
                    widgetAction = null
                }
            }
            val theme by model.theme.collectAsState()
            val look by model.look.collectAsState()
            val dark = look.dark
            // Android is told which theme the app uses, so the screen it shows while the app starts is dark or light to match.
            val loaded = model.loaded
            LaunchedEffect(theme, dark, loaded) {
                if (loaded && Build.VERSION.SDK_INT >= 31) getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                    when {
                        theme == "phone" -> UiModeManager.MODE_NIGHT_AUTO
                        dark -> UiModeManager.MODE_NIGHT_YES
                        else -> UiModeManager.MODE_NIGHT_NO
                    },
                )
            }
            // Status and navigation bar icons have to follow the app's theme, not the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            CleanRoot(model, platform, phoneStill)
        }
    }
}

