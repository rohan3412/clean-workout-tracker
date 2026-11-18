package clean.app

import androidx.compose.ui.platform.testTag
import clean.app.ui.theme.PillShape
import clean.app.ui.theme.SheetTopShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.createChildTransition
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import clean.app.ui.theme.orange
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
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import clean.app.ui.theme.cardTopShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.animation.SizeTransform
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
import clean.app.ui.theme.CleanTheme
import clean.app.ui.theme.LocalLook
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
 * `clean://start?template=Push`, `clean://weight`, `clean://resume`, `clean://open?tab=history`, `tab=body`, `tab=home`, `tab=progress`, `tab=library` (or `exercises`), `tab=workout` for the Workout sheet and `tab=profile` for the Profile page).
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
            // A templates file opened with Clean, or shared to it, goes to the Workout sheet's import.
            LaunchedEffect(sharedFile) {
                sharedFile?.let {
                    model.stack.clear()
                    model.openWorkoutSheet()
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
            Motion.moving = model.flag("ui.motion") && !phoneStill
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
            val textSize by model.textSize.collectAsState()
            val scale = when (textSize) {
                "Smallest" -> 0.72f
                "Small" -> 0.8f
                "Large" -> 1f
                "Largest" -> 1.12f
                else -> DEFAULT_TEXT_SCALE
            }
            CleanTheme(look, scale) {
                val context = androidx.compose.ui.platform.LocalContext.current
                // Glass blurs only on Android 12+, and not with Reduce transparency or the phone's own high-contrast text setting.
                val solidGlass = model.flag("glass.reduce", false) || android.os.Build.VERSION.SDK_INT < 31 || remember(context) {
                    try { android.provider.Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 } catch (e: Exception) { false }
                }
                CompositionLocalProvider(LocalMotion provides (model.flag("ui.motion") && !phoneStill), LocalSolidGlass provides solidGlass) { Surface(Modifier.fillMaxSize()) { App(model) } }
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

@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.animation.core.ExperimentalTransitionApi::class)
@Composable
fun App(model: AppModel = viewModel()) {
    BackHandler(enabled = model.stack.isNotEmpty()) { model.back() }
    // On a tab other than Home, Back goes to Home first; on Home it leaves the app.
    BackHandler(enabled = backGoesHome(model.stack.isEmpty(), model.tab)) { model.tab = Tab.Home }
    // The Workout sheet closes first.
    BackHandler(enabled = model.workoutSheet) { model.workoutSheet = false }
    val running = model.draft != null
    // The Undo bar goes when its time is up; a further step moves the time on.
    LaunchedEffect(model.undoUntil) {
        if (model.undos == null) return@LaunchedEffect
        delay((model.undoUntil - System.currentTimeMillis()).coerceAtLeast(0))
        model.undoExpired()
    }
    // The first-run questionnaire is a page of its own: no tabs, no plus button.
    if (model.welcoming) {
        Box(Modifier.fillMaxSize()) {
            WelcomeScreen(model)
            NoticeToast(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
        }
        return
    }
    // Once after an update, over whatever page the app opened on.
    model.whatsNew?.let { WhatsNewPopup(it) { model.closeWhatsNew() } }
    // Every chart can ask for the full screen through this.
    CompositionLocalProvider(LocalFullChart provides { chart -> model.fullChart = chart; model.open(Screen.Chart) }, LocalSeen provides model.seen, LocalHoldHost provides model.holds) {
    // A hold ends when the page or the tab changes under it.
    LaunchedEffect(model.stack.size, model.tab) { model.holds.hold = null }
    val motion = LocalMotion.current
    val cards = remember { CardBounds() }
    val clip = remember { MorphClip() }
    val morphTexts = remember { androidx.compose.runtime.mutableStateMapOf<String, MorphTextSpec>() }
    val chartLines = remember { androidx.compose.runtime.mutableStateMapOf<String, ChartLine>() }
    val window = LocalWindowInfo.current.containerSize
    // A page keeps where it was scrolled to (and what else it remembers) while it is under others, so Back returns
    // to the same spot; what it kept is dropped once it has left the stack.
    val pages = rememberSaveableStateHolder()
    val kept = remember { HashSet<String>() }
    val open = model.stack.map { it.toString() }
    LaunchedEffect(open) { kept.filter { it !in open }.forEach { pages.removeState(it); kept.remove(it) } }
    // The tabs page stays composed under the pages, so on Back the card it grows from is already where it is.
    val onTabs = model.stack.isEmpty()
    // The tabs page is not built again on Back, so what its cards work out from the time now (fatigue) is asked for again here.
    LaunchedEffect(onTabs) { if (onTabs) model.refreshMuscles() }
    val lastBottom = remember { arrayOfNulls<Screen>(1) }
    SideEffect { model.stack.firstOrNull()?.let { lastBottom[0] = it } }
    // The card the change under way (a page opening or closing) grows out of or shrinks into, decided once per change: none unless the
    // page was opened by a card's own tap, the card is still on the screen and in the tab shown; then it is the blink.
    val card: String? = remember(onTabs) {
        val key = model.cardKeyOf(if (onTabs) lastBottom[0] else model.stack.firstOrNull())
        val spot = key?.let { cards.onScreen(it, window.width.toFloat(), window.height.toFloat()) }
        if (!motion || key == null || spot == null || tabOfCard(key) != model.tab) null
        else { clip.begin(spot, Size(window.width.toFloat(), window.height.toFloat())); key }
    }
    // The card change is under way until its clock has run (and a little over); pages and cards take part in it only until then.
    val change = remember(onTabs) { Any() }
    var settled by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(change) { if (card != null) { delay(Motion.CARD_TO_PAGE + 80L); settled = change } }
    val morphing = card != null && settled !== change
    val morphingNow = rememberUpdatedState(morphing).let { state -> { state.value } }
    // A change between two pages is the same thing when a card on the page left (or returned to) opened the other: [link] names that
    // card and the page that grew out of it. The page under it may be composed again on Back, so its card is looked for where it was last
    // (`CardBounds.kept`); none unless the motion is on, the card is known and the page was opened by that card's own tap.
    val top = model.stack.lastOrNull()
    val lastTop = remember { arrayOfNulls<Screen>(1) }
    SideEffect { lastTop[0] = model.stack.lastOrNull() }
    val link: PageLink? = remember(top) {
        val before = lastTop[0]
        if (!motion || top == null || before == null || before == top) null
        else {
            val opening = model.stack.size >= 2 && model.stack[model.stack.size - 2] == before
            val grown = if (opening) top else before
            val under = if (opening) before else top
            val key = model.cardKeyOf(grown)
            val spot = key?.let { cards.kept(it, window.width.toFloat(), window.height.toFloat()) }
            if (key == null || spot == null || model.cardPageOf(grown) != under.toString()) null
            else { clip.begin(spot, Size(window.width.toFloat(), window.height.toFloat())); PageLink(key, grown) }
        }
    }
    val pageChange = remember(top) { Any() }
    var pageSettled by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(pageChange) { if (link != null) { delay(Motion.CARD_TO_PAGE + 80L); pageSettled = pageChange } }
    val pageMorphing = link != null && pageSettled !== pageChange
    val pageMorphingNow = rememberUpdatedState(pageMorphing).let { state -> { state.value } }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    val shared = this
    val tabsMove = updateTransition(onTabs, "tabs")
    val tabsVisible = tabsMove.createChildTransition { if (it) EnterExitState.Visible else EnterExitState.PostExit }
    val tabsScope = remember(tabsVisible) { TabsScope(tabsVisible) }
    // The tabs page: a blink with the pages, or, under a growing card, solid until the container has covered it (and from the first frame of Back).
    val tabsAlpha by tabsMove.animateFloat({
        if (!motion) snap() else if (card != null) Motion.tabsHold(!targetState) else if (targetState) Motion.blinkInSpec() else Motion.blinkOutSpec()
    }, "tabs alpha") { if (it) 1f else 0f }
    val barOut = tabsMove.animateFloat({ if (!motion || card == null) snap() else Motion.cardBounds(!targetState) }, "bar") { if (it) 0f else 1f }
    CompositionLocalProvider(
        LocalSharedScope provides shared, LocalMorphClip provides clip, LocalMorphCard provides card, LocalCardBounds provides cards, LocalMorphTexts provides morphTexts, LocalChartLines provides chartLines, LocalBarOut provides barOut,
        LocalPageMorphing provides pageMorphingNow, LocalPageMorphCard provides link?.key,
        LocalPageForward provides { model.stack.isNotEmpty() }, LocalMorphing provides morphingNow, LocalCardPress provides { key, tap -> model.fromCard(key, tap) },
    ) {
    Box(Modifier.fillMaxSize()) {
    // The Workout sheet shrinks and dims what is under it (B6).
    val sheetShown = rememberWorkoutSheetProgress(model, motion)
    // The sheet blurs what is under it; nothing is recorded for it while it is shut.
    val sheetHaze = rememberHazeState()
    val sheetUp by remember { derivedStateOf { model.workoutSheet || sheetShown.value > 0f } }
    // Any other sheet (a window of its own) shrinks the page a little too (B6); a hold blurs it (B5).
    val smallSheet by animateFloatAsState(if (SheetDepth.open > 0) 1f else 0f, if (motion) tween(200, easing = FastOutSlowInEasing) else snap(), label = "sheet depth")
    val blurred = model.holds.hold != null && !LocalSolidGlass.current
    Box(
        Modifier.fillMaxSize().graphicsLayer { val k = (1f - 0.07f * sheetShown.value) * (1f - 0.04f * smallSheet); scaleX = k; scaleY = k }
            .then(if (blurred) Modifier.blur(12.dp) else Modifier).then(if (sheetUp) Modifier.hazeSource(sheetHaze) else Modifier),
    ) {
        // Every page change but a card opening its page, forward or Back, is the blink: the page left fades out, then the new one fades in, in place (Motion).
        CompositionLocalProvider(LocalPageScope provides tabsScope, LocalPageCard provides null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = tabsAlpha }.then(if (onTabs) Modifier else Modifier.clearAndSetSemantics { }).testTag("page:tabs")) {
                pages.SaveableStateProvider("tabs") { RunningLook(running) { Box(Modifier.fillMaxSize()) { Tabs(model) } } }
            }
        }
        // While a page is open the tabs under it take no touch.
        Box(Modifier.fillMaxSize().then(if (onTabs) Modifier else Modifier.pointerInput(Unit) { })) {
        AnimatedContent(
            model.stack.lastOrNull(),
            transitionSpec = {
                // The card's page stays under the other until the container is done, and is there from the first frame going back.
                val toCard = if (link != null) initialState != null && targetState != null && (initialState == link.page || targetState == link.page)
                else card != null && ((initialState == null) != (targetState == null))
                when {
                    !motion -> EnterTransition.None togetherWith ExitTransition.None
                    // The container grows out of the card (or back into it) and the tabs page stays under it until it is done.
                    toCard -> EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
                    else -> Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion)
                }
            },
            label = "page",
        ) { screen ->
        val tabCard = if (card != null && screen != null && screen == (if (onTabs) lastBottom[0] else model.stack.firstOrNull())) card else null
        // The page that grew out of a card on the page under it, and the page under it, which stays what it was (a page that itself grew from a tab card keeps that card too).
        val grownFrom = if (link != null && screen == link.page) link.key else null
        val pageCard = grownFrom ?: tabCard
        // How far this page is along a page card's change, on its own transition (the clock of the container's bounds): up for the page that grows, down for the one under it.
        val still = !motion || link == null
        val growing = transition.animateFloat({ if (still) snap() else Motion.cardBounds(true) }, "grown") { if (it == EnterExitState.Visible) 1f else 0f }
        val beneath = transition.animateFloat({ if (still) snap() else Motion.cardBounds(true) }, "under") { if (it == EnterExitState.Visible) 0f else 1f }
        val pageProgress = if (grownFrom != null) growing else beneath
        // A page just opened by the blink lets its first rows rise (Roll.kt); once its window has run, rows composed later do not.
        val settle = screen?.let { model.settles[it.toString()] }
        LaunchedEffect(settle) { if (settle != null && settle.armed) { delay(Motion.settleWindowMillis()); settle.armed = false } }
        CompositionLocalProvider(LocalPageScope provides this, LocalPageCard provides pageCard, LocalPageProgress provides pageProgress, LocalSettle provides settle) {
        pages.SaveableStateProvider(screen?.toString()?.also { kept.add(it) } ?: "none") {
        Box(Modifier.fillMaxSize().then(if (screen == null) Modifier else Modifier.testTag("page:" + screen::class.simpleName)).cardTarget(pageCard, if (grownFrom != null) pageMorphing else morphing)) {
        // Linen draws a white sheet under the pages that have a bar; the tabs, the full-screen chart and photos, and the arrange pages do not.
        val page: @Composable () -> Unit = { when (screen) {
            null -> Unit
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
            Screen.Profile -> ProfileScreen(model)
            Screen.Photos -> PhotosScreen(model)
            is Screen.Photo -> PhotoScreen(model, screen.name, screen.pose)
            is Screen.PhotoCompare -> PhotoCompareScreen(model, screen.pose)
        } }
        // The running look tints every page but the workout's own.
        RunningLook(running && screen != Screen.Active) {
            if (screen != null) page()
        }
        }
        }
        }
        }
        }
        // The container's outline, above everything, in the same coordinates the container is drawn in.
        if (card != null) { CardEdge(); ChartMorphLine(card) } else if (link != null) CardEdge()
    }
    RunningLook(running) { WorkoutSheet(model, sheetShown, sheetHaze) }
    HoldOverlay(model.holds)
        // At the top, clear of the number pad below; on the workout, under its Finish button and rest clock.
        NoticeToast(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == Screen.Active) 56.dp else 0.dp))
        UndoBar(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == Screen.Active) 56.dp else 0.dp))
        TemplateOfferDialog(model)
        ClashSheet(model)
    }
    }
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
        SheetRow("Resume \"${draft.name} · ${clock((now - draft.startMillis) / 1000)}\"", CleanIcons.DumbbellLine) {
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

/** The card on one page that opened another, and the page that grew out of it (see the page change in [App]). */
private class PageLink(val key: String, val page: Screen)

private val BLINK_TOTAL =(Motion.BLINK_OUT_MS + Motion.BLINK_IN_MS).toFloat()
private const val BLINK_REST = -1f
/** The tab blink's opacity at [t] ms: out over [Motion.BLINK_OUT_MS], then in over [Motion.BLINK_IN_MS]; 1 at rest. */
private fun blinkAlpha(t: Float): Float = when {
    t < 0f || t >= BLINK_TOTAL -> 1f
    t < Motion.BLINK_OUT_MS -> 1f - t / Motion.BLINK_OUT_MS
    else -> (t - Motion.BLINK_OUT_MS) / Motion.BLINK_IN_MS
}

/** The three tabs in the order the bar and the pager lay them out, so a swipe goes the way the bar reads. */
private val TAB_ORDER = listOf(Tab.Home, Tab.Progress, Tab.Exercises)

/** The tab bar and the plus button beside it: 60 dp tall, floating 8 dp above the bottom with 16 dp at the sides and 8 dp between them. */
private val BAR_HEIGHT = 60.dp
private val BAR_GAP = 8.dp

@Composable
private fun Tabs(model: AppModel) {
    val ends = remember { mutableStateMapOf<Tab, Boolean>() }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val order = TAB_ORDER
    // Constant whether a workout runs or not, so a list never changes what it can scroll.
    val inset = BAR_HEIGHT + BAR_GAP + nav
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.statusBars) { padding ->
        // The tabs lie side by side: a swipe goes to the next one, a tap on the bar glides there.
        val pager = rememberPagerState(order.indexOf(model.tab)) { order.size }
        val motion = LocalMotion.current
        // Where a tap on the bar's blink is, in ms (none at rest): the pager fades out, changes page at the midpoint and fades in, on one clock.
        val blink = remember { Animatable(BLINK_REST) }
        LaunchedEffect(model.tab) {
            val page = order.indexOf(model.tab)
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
        LaunchedEffect(order) {
            pager.scrollToPage(order.indexOf(model.tab))
            snapshotFlow { pager.targetPage }.collect { if (model.tab != order[it]) model.tab = order[it] }
        }
        // Once the first tab is up, its neighbours are kept ready, so a swipe does not start by building a page.
        val warm by produceState(false) { delay(800); value = true }
        // The pages are what the bar and the plus button blur; nothing is recorded while a page covers the tabs.
        val haze = rememberHazeState()
        val onTabs = model.stack.isEmpty()
        Box(Modifier.padding(padding).fillMaxSize()) {
            CompositionLocalProvider(LocalBarEnds provides ends, LocalBarInset provides inset, LocalHaze provides haze) {
                HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer { alpha = blinkAlpha(blink.value) }.then(if (onTabs) Modifier.hazeSource(haze) else Modifier), beyondViewportPageCount = if (warm) 1 else 0) { page ->
                    when (order[page]) {
                        Tab.Home -> HomeTab(model)
                        Tab.Progress -> ProgressTab(model)
                        Tab.Exercises -> ExercisesTab(model)
                    }
                }
            }
            // The bar belongs to the tabs page: it goes and comes back in the same blink as the page, or under a growing card slides away with it.
            val barOut = LocalBarOut.current
            // A card that lies partly behind the bar is drawn only above the bar while it is there (see [MorphClip.barTop]), so what the bar hides stays hidden
            // from the tap on and shows as the bar leaves.
            val clipForBar = LocalMorphClip.current
            val barSpot = remember { floatArrayOf(Float.MAX_VALUE, 0f) }
            val barSlide = with(LocalDensity.current) { 48.dp.toPx() }
            SideEffect { clipForBar?.barTop = { barSpot[0] + (barOut?.value ?: 0f) * (barSpot[1] + barSlide) } }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.l).padding(bottom = BAR_GAP + nav)
                    .onGloballyPositioned { barSpot[0] = it.positionInRoot().y; barSpot[1] = it.size.height.toFloat() }
                    .graphicsLayer { translationY = (barOut?.value ?: 0f) * (size.height + 48.dp.toPx()) },
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP), verticalAlignment = Alignment.CenterVertically,
            ) {
                TabBar(
                    model, order, Modifier.weight(1f),
                    // A swipe moves the pill with the finger; a tap's blink glides it on its own clock.
                    follow = { if (pager.isScrollInProgress && blink.value == BLINK_REST) pager.currentPage + pager.currentPageOffsetFraction else null },
                )
                PlusButton(model)
            }
        }
    }
}

/**
 * The three tabs as a glass bar over the page: an outline icon with its word under it (the words give way while a workout runs, to
 * leave the plus button room), the selected tab in a large soft pill (white at 11 % in Dark, black at 7 % in Light). [current] is the
 * tab drawn as selected; Settings' live preview draws the bar with one that is not the app's own, under a layer that takes no touch.
 */
@Composable
internal fun TabBar(model: AppModel, order: List<Tab>, modifier: Modifier, current: Tab = model.tab, follow: () -> Float? = { null }) {
    // One pill glides behind the slots (Motion.pill) from the old tab to the new, its leading edge first, or follows a swipe (follow, in tabs).
    val span = rememberStretch(order.indexOf(current).coerceAtLeast(0), follow)
    val pillColour = if (LocalLook.current.dark) ComposeColor.White.copy(alpha = 0.11f) else ComposeColor.Black.copy(alpha = 0.07f)
    val words = model.draft == null
    Row(
        modifier.height(BAR_HEIGHT).glass(PillShape).padding(Space.xs)
            .drawBehind {
                val s = span()
                val slot = size.width / order.size
                val inset = 1.dp.toPx()
                val left = s.left { it * slot + inset }
                val right = s.right { (it + 1) * slot - inset }
                drawRoundRect(pillColour, androidx.compose.ui.geometry.Offset(left, 0f), Size(right - left, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((i, tab) in order.withIndex()) TabItem(model, tab, current == tab, i, span, words)
    }
}

/** One slot of the bar, the same width for every tab, its content centred in the bar's height. Role Tab with the selected state and [spoken] as its name. */
@Composable
private fun RowScope.TabSlot(selected: Boolean, spoken: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(PillShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = spoken },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** One tab: its outline icon (24 dp) and, while there is room, its word. The icon and word change colour as the gliding pill reaches them. */
@Composable
private fun RowScope.TabItem(model: AppModel, tab: Tab, selected: Boolean, index: Int, span: () -> Span, words: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val cover = span().cover(index)
    val tint = androidx.compose.ui.graphics.lerp(scheme.onSurfaceVariant, scheme.onSurface, cover)
    val icon = when (tab) {
        Tab.Home -> CleanIcons.TabHome
        Tab.Progress -> CleanIcons.TabProgress
        Tab.Exercises -> CleanIcons.TabList
    }
    TabSlot(selected, tab.label, { model.tab = tab }) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        if (words) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                tab.label, Modifier.clearAndSetSemantics { }, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (cover > 0.5f) FontWeight.Bold else FontWeight.Medium, color = tint,
            )
        }
    }
}

/**
 * The round glass button beside the bar, as tall as it: a plus that raises the Workout sheet. With a workout running it is a wider pill
 * with a pulsing accent dot, the workout's name and its clock (or the rest counting down), and a tap goes back to the workout.
 */
@Composable
private fun PlusButton(model: AppModel) {
    val draft = model.draft
    val motion = LocalMotion.current
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down && motion) 0.94f else 1f, Motion.pressNow(), label = "press")
    val ink = MaterialTheme.colorScheme.onSurface
    // Spoken: what a tap does, with the workout's name and how long it has run.
    val minutes = draft?.let { (nowMillis() - it.startMillis) / 60_000 }
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.height(BAR_HEIGHT).widthIn(min = BAR_HEIGHT).glass(CircleShape)
            .clickable(source, LocalIndication.current, role = Role.Button) { if (draft != null) model.resumeWorkout() else model.workoutSheet = true }
            .semantics { contentDescription = if (draft != null) "Back to workout ${draft.name}, ${plural(minutes?.toInt() ?: 0, "minute")}" else "Start a workout" },
        contentAlignment = Alignment.Center,
    ) {
        // The plus grows into the clock pill and folds back (C8): the glass follows the width of what is inside, and the content crosses over.
        val last = remember { arrayOfNulls<Draft>(1) }
        if (draft != null) last[0] = draft
        AnimatedContent(
            draft != null,
            transitionSpec = {
                if (motion) (fadeIn(Motion.quick()) togetherWith fadeOut(Motion.quick())).using(SizeTransform(clip = false) { _, _ -> Motion.pill() })
                else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "plus",
        ) { running ->
            val shown = last[0]
            if (!running || shown == null) {
                Box(Modifier.size(BAR_HEIGHT), contentAlignment = Alignment.Center) { Icon(CleanIcons.Add, null, Modifier.size(28.dp), tint = ink) }
            } else {
                val now = nowMillis()
                val rest = model.restEnd?.let { (it - now) / 1000 }?.takeIf { it > 0 }
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.3f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "dot")
                Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).graphicsLayer { alpha = if (motion) pulse else 1f }.background(LocalLook.current.orange, CircleShape))
                    if (rest == null) Text(shown.name, Modifier.widthIn(max = 96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
                    Text(
                        if (rest != null) "Rest ${clock(rest)}" else clock((now - shown.startMillis) / 1000), maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink,
                    )
                }
            }
        }
    }
}

/** How far the Workout sheet is up, 0 (closed) to 1 (open); it follows [AppModel.workoutSheet]. */
@Composable
internal fun rememberWorkoutSheetProgress(model: AppModel, motion: Boolean): androidx.compose.runtime.State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(model.workoutSheet) {
        val target = if (model.workoutSheet) 1f else 0f
        if (motion) progress.animateTo(target, tween(280, easing = FastOutSlowInEasing)) else progress.snapTo(target)
    }
    return progress.asState()
}

/**
 * The Workout sheet (F6): about 90 % of the screen, over the current page, which the app shrinks and dims behind it. Closed by a tap
 * outside, a swipe down on its top, or Back. What is inside is [WorkoutSheetContent].
 */
@Composable
private fun WorkoutSheet(model: AppModel, shown: androidx.compose.runtime.State<Float>, haze: HazeState) {
    val open = model.workoutSheet
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) { if (open) drag.snapTo(0f) }
    val p = shown.value
    if (!open && p <= 0f) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = p }.background(ComposeColor.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { model.workoutSheet = false }.semantics { contentDescription = "Close" },
        )
        var height by remember { mutableStateOf(1) }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.9f).onSizeChanged { height = it.height.coerceAtLeast(1) }
                .graphicsLayer { translationY = (1f - shown.value) * size.height + drag.value }
                .glass(SheetTopShape, haze).semantics { paneTitle = "Start a workout" }.clickable(remember { MutableInteractionSource() }, null) { },
        ) {
            WorkoutSheetContent(
                model,
                Modifier.draggable(
                    rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                    onDragStopped = { if (drag.value > height / 5f) model.workoutSheet = false else scope.launch { drag.animateTo(0f, Motion.follow()) } },
                ),
            )
        }
    }
}
