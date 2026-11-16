package clean.app

import androidx.compose.ui.platform.testTag
import clean.app.ui.theme.orange
import clean.app.ui.theme.numbers
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import clean.app.ui.theme.cardTopShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import android.app.UiModeManager
import android.os.Build
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color as ComposeColor
import clean.app.ui.theme.DEFAULT_TEXT_SCALE
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import clean.app.ui.theme.BlackBar
import clean.app.ui.theme.BlackBarQuiet
import clean.app.ui.theme.CleanTheme
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.paperGrain
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the app was opened to do, as an action for [AppModel.widgetAction]: from a widget or a
 * launcher shortcut (the `widget` extra), or from a link in another app (`clean://start`,
 * `clean://start?template=Push`, `clean://weight`, `clean://resume`, `clean://open?tab=history`, `tab=body`, `tab=home`).
 */
private fun actionOf(intent: Intent): String? = intent.getStringExtra("widget")?.takeIf { it.isNotEmpty() }
    ?: intent.data?.takeIf { it.scheme == "clean" }?.let { link ->
        when (link.host) {
            "start" -> link.getQueryParameter("template")?.let { "templateName:$it" } ?: "start"
            "open" -> link.getQueryParameter("tab")?.let { "tab:$it" }
            "weight" -> "weight"
            "resume" -> "workout"
            else -> null
        }
    }

class MainActivity : ComponentActivity() {
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

    private val model: AppModel by viewModels()

    // A rest that ends while the app is behind is carried by an alarm (see RestAlert); in front, the app counts it itself.
    override fun onStart() {
        super.onStart()
        phoneStill = Motion.systemAnimationsOff(this)
        model.onStarted()
    }

    override fun onStop() {
        super.onStop()
        model.onStopped()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            // A templates file opened with Clean, or shared to it, goes to the Workout tab's import.
            LaunchedEffect(sharedFile) {
                sharedFile?.let {
                    model.stack.clear()
                    model.tab = Tab.Workout
                    model.sharedFile = it
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
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { model.systemDark.value = systemDark }
            val dark = look.dark
            Motion.speed = look.speed
            Motion.moving = model.flag("ui.motion") && !phoneStill
            // Android is told which theme the app uses, so the screen it shows while the app starts is dark or light to match.
            val loaded = model.loaded
            LaunchedEffect(theme, dark, loaded) {
                if (loaded && Build.VERSION.SDK_INT >= 31) getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                    when {
                        theme == "System" -> UiModeManager.MODE_NIGHT_AUTO
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
            val textSize by model.textSize.collectAsState()
            val font by model.font.collectAsState()
            val chartStyle by model.chartStyle.collectAsState()
            val scale = when (textSize) {
                "Smallest" -> 0.72f
                "Small" -> 0.8f
                "Large" -> 1f
                "Largest" -> 1.12f
                else -> DEFAULT_TEXT_SCALE
            }
            CleanTheme(look, scale, font?.substringBefore('|')) {
                CompositionLocalProvider(LocalMotion provides (model.flag("ui.motion") && !phoneStill), LocalChartStyle provides chartStyle) { Surface(Modifier.fillMaxSize().paperGrain(if (look.sheetPage) 0f else look.texture, look.dark)) { App(model) } }
            }
        }
    }
}

/** Wall-clock time that updates once a second, for timers. */
@Composable
fun nowMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

@Composable
fun App(model: AppModel = viewModel()) {
    BackHandler(enabled = model.stack.isNotEmpty()) { model.back() }
    // On a tab other than Home, Back goes to Home first; on Home it leaves the app.
    BackHandler(enabled = backGoesHome(model.stack.isEmpty(), model.tab)) { model.tab = Tab.Home }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.notice) {
        val notice = model.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice.text)
        if (model.notice === notice) model.notice = null
    }
    // The Undo bar goes when its time is up; a further step moves the time on.
    LaunchedEffect(model.undoUntil) {
        if (model.undos == null) return@LaunchedEffect
        delay((model.undoUntil - System.currentTimeMillis()).coerceAtLeast(0))
        model.undoExpired()
    }
    // The first-run questionnaire is a page of its own: no tabs, no workout banner.
    if (model.welcoming) {
        Box(Modifier.fillMaxSize()) {
            WelcomeScreen(model)
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
        }
        return
    }
    // Once after an update, over whatever page the app opened on.
    model.whatsNew?.let { WhatsNewPopup(it) { model.closeWhatsNew() } }
    // Every chart can ask for the full screen through this.
    CompositionLocalProvider(LocalFullChart provides { chart -> model.fullChart = chart; model.open(Screen.Chart) }) {
    Box(Modifier.fillMaxSize()) {
        // Every page change, forward or Back, is the blink: the page left fades out, then the new one fades in, in place (Motion).
        val motion = LocalMotion.current
        // A page keeps where it was scrolled to (and what else it remembers) while it is under others, so Back returns
        // to the same spot; what it kept is dropped once it has left the stack.
        val pages = rememberSaveableStateHolder()
        val kept = remember { HashSet<String>() }
        val open = model.stack.map { it.toString() }
        LaunchedEffect(open) { kept.filter { it !in open }.forEach { pages.removeState(it); kept.remove(it) } }
        AnimatedContent(
            model.stack.lastOrNull(),
            transitionSpec = { Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion) },
            label = "page",
        ) { screen ->
        pages.SaveableStateProvider(screen?.toString()?.also { kept.add(it) } ?: "tabs") {
        Box(Modifier.fillMaxSize().testTag("page:" + (screen?.let { it::class.simpleName } ?: "tabs"))) {
        // Linen draws a white sheet under the pages that have a bar; the tabs, the full-screen chart and photos, and the arrange pages do not.
        val page: @Composable () -> Unit = { when (screen) {
            null -> Box(Modifier.fillMaxSize()) { GrainLayer(); Tabs(model) }
            Screen.Active -> ActiveScreen(model)
            Screen.Correct -> CorrectScreen(model)
            Screen.Settings -> SettingsScreen(model)
            is Screen.WorkoutDetail -> WorkoutDetailScreen(model, screen.date)
            is Screen.ExerciseDetail -> ExerciseDetailScreen(model, screen.name, screen.edit, screen.tab)
            Screen.Chart -> FullChartScreen(model)
            is Screen.Summary -> SummaryScreen(model, screen.date)
            is Screen.Week -> WeekScreen(model, screen.monday)
            is Screen.MeasurementDetail -> MeasurementDetailScreen(model, screen.file)
            is Screen.MuscleDetail -> MuscleDetailScreen(model, screen.muscle)
            is Screen.HeadDetail -> HeadDetailScreen(model, screen.head)
            is Screen.Routine -> RoutineScreen(model, screen.name)
            is Screen.Month -> MonthScreen(model, screen.month)
            is Screen.PrTimeline -> PrTimelineScreen(model, screen.exercise)
            is Screen.Calendar -> CalendarScreen(model, screen.day)
            Screen.Bmi -> BmiScreen(model)
            is Screen.Overall -> OverallScreen(model, screen.exercise)
            is Screen.NoteReview -> NoteReviewScreen(model, screen.exercise)
            Screen.ExerciseSetup -> ExerciseSetupScreen(model)
            is Screen.TemplateEdit -> TemplateEditScreen(model, screen.id)
            is Screen.TemplateStats -> TemplateScreen(model, screen.id)
            Screen.Trends -> TrendsScreen(model)
            Screen.Trophies -> TrophiesScreen(model)
            Screen.Program -> ProgramScreen(model)
            Screen.PersonalBests -> PersonalBestsScreen(model)
            Screen.History -> HistoryScreen(model)
            Screen.Body -> BodyScreen(model)
            Screen.Measurements -> MeasurementsScreen(model)
            Screen.EditHome -> EditHomeScreen(model)
            Screen.EditProfile -> EditProfileScreen(model)
            Screen.Navigation -> NavigationScreen(model)
            Screen.Shortcuts -> ShortcutsScreen(model)
            Screen.Photos -> PhotosScreen(model)
            is Screen.Photo -> PhotoScreen(model, screen.name, screen.pose)
            is Screen.PhotoCompare -> PhotoCompareScreen(model, screen.pose)
        } }
        if (screen == null || screen == Screen.Chart || screen == Screen.EditHome || screen == Screen.EditProfile || screen is Screen.Photo || screen is Screen.PhotoCompare) page() else SheetHost(page)
        }
        }
        }
        // On the pages behind the tabs the bar sits at the bottom of the screen; on the tabs it is above the buttons.
        val bannerScope by model.bannerScope.collectAsState()
        val top = model.stack.lastOrNull()
        if (bannerScope == "Everywhere" && top != null && top != Screen.Active && top != Screen.Chart && top !is Screen.Photo && top !is Screen.PhotoCompare) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { WorkoutBanner(model) }
        }
        // At the top, clear of the banner and the number pad below; on the workout, under its Finish button and rest clock.
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == Screen.Active) 56.dp else 0.dp))
        UndoBar(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == Screen.Active) 56.dp else 0.dp))
        TemplateOfferDialog(model)
        ClashSheet(model)
    }
    }
}

/** Asked when a workout is started while another runs: go back to the running one, or throw it away for the new one. */
@Composable
private fun ClashSheet(model: AppModel) {
    val clash = model.clash ?: return
    val draft = model.draft
    // The running workout was finished or cancelled meanwhile: there is nothing left to ask.
    if (draft == null) {
        LaunchedEffect(clash) { model.clash = null }
        return
    }
    val now = nowMillis()
    Sheet({ model.clash = null }) {
        SheetRow("Resume \"${draft.name} · ${clock((now - draft.startMillis) / 1000)}\"", CleanIcons.Dumbbell) {
            model.clash = null
            model.resumeWorkout()
        }
        SheetRow("Discard it and start \"${clash.name}\"", danger = true) {
            model.clash = null
            // A new workout takes its place at once, so there is nothing to give back.
            model.cancelWorkout(undoable = false)
            clash.action()
        }
        SheetGap()
        SheetRow("Back") { model.clash = null }
    }
}

/** The bar that shows a workout is in progress, with its clock or the rest counting down; a tap goes to it. For the first five minutes it also has Cancel. */
@Composable
fun WorkoutBanner(model: AppModel, shape: Shape = cardTopShape()) {
    val draft = model.draft ?: return
    val now = nowMillis()
    var cancelling by remember { mutableStateOf(false) }
    val rest = model.restEnd?.let { (it - now) / 1000 }?.takeIf { it > 0 }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer, shape = shape,
        modifier = Modifier.fillMaxWidth().clickable { model.resumeWorkout() },
    ) {
        Column {
            // Always a finger's height, so the bar does not change size when Cancel goes after five minutes; the clock stays at the right edge.
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(CleanIcons.Dumbbell, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(draft.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (draft.isEarly(now)) DangerButton("Cancel", { cancelling = true }, Modifier.minTouch())
                Text(if (rest != null) "Rest ${clock(rest)}" else clock((now - draft.startMillis) / 1000), style = LocalTextStyle.current.numbers(), fontWeight = FontWeight.SemiBold)
            }
            // The rest draining away along the bottom edge.
            val end = model.restEnd
            if (rest != null && end != null && model.restTotal > 0) {
                Box(Modifier.fillMaxWidth(((end - now) / (model.restTotal * 1000f)).coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
    if (cancelling) Confirm("Cancel workout?", "Everything entered in this workout is thrown away.", "Discard", { model.cancelWorkout() }) { cancelling = false }
}

private val BLINK_TOTAL = (Motion.BLINK_OUT_MS + Motion.BLINK_IN_MS).toFloat()
private const val BLINK_REST = -1f
/** The tab blink's opacity at [t] ms: out over [Motion.BLINK_OUT_MS], then in over [Motion.BLINK_IN_MS]; 1 at rest. */
private fun blinkAlpha(t: Float): Float = when {
    t < 0f || t >= BLINK_TOTAL -> 1f
    t < Motion.BLINK_OUT_MS -> 1f - t / Motion.BLINK_OUT_MS
    else -> (t - Motion.BLINK_OUT_MS) / Motion.BLINK_IN_MS
}

/** The tab bar: 64 dp tall, floating 8 dp above the bottom with 16 dp at the sides, and docked flat on the bottom edge when a list is scrolled to its end. */
private val BAR_HEIGHT = 68.dp
private val BAR_GAP = 8.dp
/** The banner's height in the bar, a little over its 48 dp (the rest drains along its foot). */
internal val BANNER_ROOM = 52.dp

@Composable
private fun Tabs(model: AppModel) {
    val bannerScope by model.bannerScope.collectAsState()
    val options by model.options.collectAsState()
    val ends = remember { mutableStateMapOf<Tab, Boolean>() }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val banner = bannerScope != "Off" && model.draft != null
    // The bar and the pager lay the tabs out in the same order, so a swipe goes the way the bar reads.
    val order = tabOrderOf(options["nav.order"])
    val orderNow by rememberUpdatedState(order)
    // Constant whether the bar floats or has settled, so settling never changes what a list can scroll.
    val inset = BAR_HEIGHT + BAR_GAP + nav + if (banner) BANNER_ROOM else 0.dp
    Scaffold(containerColor = if (LocalLook.current.sheetPage) ComposeColor.Transparent else MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.statusBars) { padding ->
        // The tabs lie side by side: a swipe goes to the next one, a tap on the bar glides there.
        val pager = rememberPagerState(order.indexOf(model.tab)) { order.size }
        val motion = LocalMotion.current
        // Where a tap on the bar's blink is, in ms (none at rest): the pager fades out, changes page at the midpoint and fades in, on one clock.
        val blink = remember { Animatable(BLINK_REST) }
        LaunchedEffect(model.tab) {
            val page = orderNow.indexOf(model.tab)
            if (pager.targetPage == page) return@LaunchedEffect
            if (motion) {
                // A tap on the bar blinks, near or far, in place. A swipe moves the pager itself, following the finger, and never comes here.
                try {
                    blink.snapTo(0f)
                    coroutineScope {
                        launch { blink.animateTo(BLINK_TOTAL, tween(Motion.BLINK_OUT_MS + Motion.BLINK_IN_MS, easing = LinearEasing)) }
                        snapshotFlow { blink.value }.first { it >= Motion.BLINK_OUT_MS }
                        pager.scrollToPage(page)
                    }
                } finally {
                    withContext(NonCancellable) { blink.snapTo(BLINK_REST) }
                }
            } else pager.scrollToPage(page)
        }
        // The bar follows the page the swipe is heading for, not the one it has come to rest on, so it moves with the finger.
        // A new order first puts the pager on the page the current tab now has, so the old index is never read as a swipe.
        LaunchedEffect(order) {
            pager.scrollToPage(order.indexOf(model.tab))
            snapshotFlow { pager.targetPage }.collect { if (model.tab != order[it]) model.tab = order[it] }
        }
        // Once the first tab is up, its neighbours are kept ready, so a swipe does not start by building a page.
        val warm by produceState(false) { delay(800); value = true }
        Box(Modifier.padding(padding).fillMaxSize()) {
            CompositionLocalProvider(LocalBarEnds provides ends, LocalBarInset provides inset) {
                HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer { alpha = blinkAlpha(blink.value) }, beyondViewportPageCount = if (warm) 1 else 0) { page ->
                    when (order[page]) {
                        Tab.Home -> HomeTab(model)
                        Tab.Progress -> ProgressTab(model)
                        Tab.Workout -> WorkoutTab(model)
                        Tab.Exercises -> ExercisesTab(model)
                        Tab.Profile -> ProfileTab(model)
                    }
                }
            }
            // The bar belongs to the tabs page: it goes and comes back in the same blink as the page.
            TabBar(model, order, ends[order[pager.currentPage.coerceIn(order.indices)]] == true, banner, nav, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * The five tabs as a rounded bar over the page. Every tab shows its word, in one of two styles (the look's tab bar style part,
 * `Look.tabWords`): words only (the selected word in a pill) or an outline icon with its word under it. Workout in the middle is a
 * pill in the accent with its word (it resumes the workout while one is in progress). The order of the four outer ones is [order].
 * With a list at its end ([settled]) the bar loses its margins and its lower corners and lies on the bottom edge. The workout banner, when shown, is docked on top of it.
 * [current] is the tab drawn as selected; Settings' live preview draws the bar with one that is not the app's own, under a layer that takes no touch.
 */
@Composable
internal fun TabBar(model: AppModel, order: List<Tab>, settled: Boolean, banner: Boolean, nav: Dp, modifier: Modifier, current: Tab = model.tab) {
    val floating = if (settled) 0f else 1f
    val corner = 24.dp
    val black = LocalLook.current.blackTabBar
    Surface(
        modifier.fillMaxWidth().padding(horizontal = Space.l * floating).padding(bottom = (BAR_GAP + nav) * floating),
        shape = RoundedCornerShape(corner * floating),
        color = if (black) BlackBar else MaterialTheme.colorScheme.surfaceContainer, border = if (black) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (banner) WorkoutBanner(model, RectangleShape)
            // Five slots of one width, so every word keeps its place and the middle one is the bar's centre.
            Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                for (tab in order) if (tab == Tab.Workout) WorkoutButton(model, current == Tab.Workout) else TabItem(model, tab, current == tab)
            }
            Spacer(Modifier.height(nav * (1f - floating)))
        }
    }
}

/** The pill behind a tab's icon (Material 3's active indicator) or its word. */
private val INDICATOR_WIDTH = 56.dp
private val INDICATOR_HEIGHT = 32.dp
/** The middle tab's larger pill (round 7: "make the middle workouts button bigger"); it holds the word "Workout". */
private val MIDDLE_WIDTH = 68.dp
private val MIDDLE_HEIGHT = 38.dp

/**
 * One slot of the bar, the same width for every tab, its content centred in the bar's height. Role Tab with the selected state and
 * [spoken] as its name; a hold calls [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TabSlot(
    selected: Boolean, spoken: String, onClick: () -> Unit, onLongClick: (() -> Unit)?, content: @Composable ColumnScope.(MutableInteractionSource) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .combinedClickable(
                interactionSource = source, indication = null,
                onLongClickLabel = if (onLongClick != null) "Arrange tabs" else null,
                onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { role = Role.Tab; this.selected = selected; contentDescription = spoken },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) { content(source) }
}

/** A tab's word, one line, bolder when selected; spoken by its slot, not by itself. */
@Composable
private fun TabWord(label: String, tint: ComposeColor, selected: Boolean) {
    Text(
        label, Modifier.clearAndSetSemantics { }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = tint,
    )
}

/**
 * One outer tab. Words only: the word, the selected one in a pill (white with dark text on the black bar, the container colour
 * otherwise). Icons and words: an outline icon in a pill that fills when selected, the word under it. A hold opens the Navigation page.
 */
@Composable
private fun RowScope.TabItem(model: AppModel, tab: Tab, selected: Boolean) {
    val look = LocalLook.current
    val black = look.blackTabBar
    val scheme = MaterialTheme.colorScheme
    val onClick = { model.tab = tab }
    val hold = { model.open(Screen.Navigation) }
    if (look.tabWords) {
        val pillColour = if (black) ComposeColor.White else scheme.secondaryContainer
        val pill = if (selected) pillColour else pillColour.copy(alpha = 0f)
        val tint = if (black) (if (selected) BlackBar else BlackBarQuiet) else if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
        TabSlot(selected, tab.label, onClick, hold) { source ->
            Box(
                Modifier.fillMaxWidth().padding(horizontal = Space.xxs).height(INDICATOR_HEIGHT).clip(CircleShape).background(pill).indication(source, LocalIndication.current),
                contentAlignment = Alignment.Center,
            ) { TabWord(tab.label, tint, selected) }
        }
    } else {
        val icon = when (tab) {
            Tab.Home -> CleanIcons.TabHome
            Tab.Progress -> CleanIcons.TabProgress
            Tab.Exercises -> CleanIcons.TabList
            else -> CleanIcons.TabPerson
        }
        val pillColour = if (black) ComposeColor.White.copy(alpha = 0.14f) else scheme.secondaryContainer
        val pill = if (selected) pillColour else pillColour.copy(alpha = 0f)
        val tint = if (black) (if (selected) ComposeColor.White else BlackBarQuiet) else if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
        val wordTint = if (black) (if (selected) ComposeColor.White else BlackBarQuiet) else if (selected) scheme.onSurface else scheme.onSurfaceVariant
        TabSlot(selected, tab.label, onClick, hold) { source ->
            Box(
                Modifier.size(INDICATOR_WIDTH, INDICATOR_HEIGHT).clip(CircleShape).background(pill).indication(source, LocalIndication.current),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
            Spacer(Modifier.height(Space.xxs))
            TabWord(tab.label, wordTint, selected)
        }
    }
}

/**
 * The middle of the bar: the Workout tab, or, with a workout running, the way back into it. A pill in the accent holding the
 * word "Workout", larger than the other slots' pills and centred in the bar in both styles. A tap blinks to the Workout page; a swipe slides it with the finger.
 */
@Composable
private fun RowScope.WorkoutButton(model: AppModel, onTab: Boolean) {
    val draft = model.draft
    val look = LocalLook.current
    val motion = LocalMotion.current
    TabSlot(
        onTab, if (draft != null) "Resume workout" else "Workout",
        onClick = { if (draft != null) model.resumeWorkout() else model.tab = Tab.Workout }, onLongClick = null,
    ) { source ->
        val down by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (down && motion) 0.94f else 1f, Motion.pressNow(), label = "press")
        Box(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.fillMaxWidth().padding(horizontal = 1.dp).widthIn(max = MIDDLE_WIDTH).height(MIDDLE_HEIGHT).clip(CircleShape)
                .background(look.orange)
                .then(if (onTab) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .indication(source, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Workout", Modifier.clearAndSetSemantics { }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = if (look.tabWords) look.orange.readableOn() else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

