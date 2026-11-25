package clean.app

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag as testTagSemantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import clean.app.ui.theme.PillShape
import clean.app.ui.theme.SheetTopShape
import androidx.compose.ui.draw.drawWithContent
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
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeProgressive
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
import kotlin.math.abs
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.aspectRatio
import kotlin.math.max
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color as ComposeColor
import clean.app.ui.theme.DEFAULT_TEXT_SCALE
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.foundation.basicMarquee
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

/** Wall-clock time that updates once a second, for timers. */
@Composable
fun nowMillis(): Long {
    val now by produceState(currentMillis()) {
        while (true) {
            value = currentMillis()
            delay(1000)
        }
    }
    return now
}

@OptIn(ExperimentalSharedTransitionApi::class, androidx.compose.animation.core.ExperimentalTransitionApi::class)
@Composable
fun App(model: AppModel) {
    // E5: predictive back. While the gesture is under way the page on top shrinks a little and rounds its corners (a uniform scale of that
    // page, not a shrink toward its card); on commit the page closes as it always did, on cancel it springs back.
    val backAmount = remember { Animatable(0f) }
    val backPage = remember { arrayOfNulls<Screen>(1) }
    val backScope = rememberCoroutineScope()
    val backMotion = LocalMotion.current
    PredictiveBackHandler(enabled = model.stack.isNotEmpty()) { events ->
        backPage[0] = model.stack.lastOrNull()
        try {
            events.collect { if (backMotion) backAmount.snapTo(it.progress) }
            model.back()
            backScope.launch { if (backMotion) backAmount.animateTo(0f, tween(180)) else backAmount.snapTo(0f); backPage[0] = null }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            withContext(NonCancellable) { if (backMotion) backAmount.animateTo(0f, Motion.follow()) else backAmount.snapTo(0f); backPage[0] = null }
            throw e
        }
    }
    // On a tab other than Home, Back goes to Home first; on Home it leaves the app.
    BackHandler(enabled = backGoesHome(model.stack.isEmpty(), model.tab)) { model.tab = Tab.Home }
    // The Workout sheet closes first.
    BackHandler(enabled = model.workoutSheet) { model.workoutSheet = false }
    val running = model.draft != null
    // The Undo bar goes when its time is up; a further step moves the time on.
    LaunchedEffect(model.undoUntil) {
        if (model.undos == null) return@LaunchedEffect
        delay((model.undoUntil - currentMillis()).coerceAtLeast(0))
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
    model.whatsNew?.let { WhatsNewPage(model.allChanges, it, rememberBackdrop(model, MaterialTheme.colorScheme.background)) { model.closeWhatsNew() } }
    // Every chart can ask for the full screen through this.
    CompositionLocalProvider(LocalFullChart provides { chart -> model.fullChart = chart; model.open(Screen.Chart) }, LocalSeen provides model.seen, LocalHoldHost provides model.holds) {
    // A hold ends when the page or the tab changes under it.
    LaunchedEffect(model.stack.size, model.tab) { model.holds.hold = null }
    LaunchedEffect(Unit) { model.checkWeekPill() }
    val motion = LocalMotion.current
    // Light (round 26) has no card-to-page growth: every page opens by the blink.
    val cardMoves = motion && LocalPerformance.current.sharedCards
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
        if (!cardMoves || key == null || spot == null || (if (key == DOCK_WORKOUT) model.draft == null else tabOfCard(key) != model.tab)) null
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
        if (!cardMoves || top == null || before == null || before == top) null
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
    // The toast and the Undo pill blur it too, while one is up (kept a moment after, while it fades away).
    var pillUp by remember { mutableStateOf(false) }
    val pillWanted = model.undos != null || model.notice != null
    LaunchedEffect(pillWanted) { if (pillWanted) pillUp = true else { delay(400); pillUp = false } }
    val sheetUp by remember { derivedStateOf { model.workoutSheet || sheetShown.value > 0f || pillUp } }
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
        val topFade = remember { TopFade() }
        val groundNow = MaterialTheme.colorScheme.background
        val pageBackdrop = remember { PageBackdrop(groundNow) }
        CompositionLocalProvider(LocalPageScope provides this, LocalPageCard provides pageCard, LocalPageProgress provides pageProgress, LocalSettle provides settle, LocalTopFade provides topFade, LocalPageBackdrop provides pageBackdrop) {
        pages.SaveableStateProvider(screen?.toString()?.also { kept.add(it) } ?: "none") {
        val backing = screen != null && screen == backPage[0]
        Box(Modifier.fillMaxSize()
            .graphicsLayer {
                if (backing && backAmount.value > 0f) {
                    val k = 1f - 0.08f * backAmount.value
                    scaleX = k; scaleY = k
                    this.shape = RoundedCornerShape((28.dp * backAmount.value).toPx()); this.clip = true
                }
            }
            .nestedScroll(topFade).then(if (screen == null) Modifier else Modifier.testTag("page:" + screen::class.simpleName)).cardTarget(pageCard, if (grownFrom != null) pageMorphing else morphing, if (screen != null) pageBackdrop else null)) {
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
            Screen.Photos -> PhotoGate(model) { PhotosScreen(model) }
            Screen.Moments -> PhotoGate(model) { PhotosScreen(model, clean.core.PhotoFilter.SNAPSHOTS) }
            is Screen.YearReview -> YearReviewScreen(model, screen.year)
            is Screen.Photo -> PhotoGate(model) { PhotoScreen(model, screen.name, screen.filter) }
            is Screen.PhotoCompare -> PhotoGate(model) { PhotoCompareScreen(model, screen.pose) }
        } }
        // The running look tints every page but the workout's own.
        RunningLook(running && screen != Screen.Active) {
            if (screen != null) { RegisterPageBackdrop(model, pageBackdrop); page() }
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
        NoticeToast(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == Screen.Active) 56.dp else 0.dp), sheetHaze)
        // Just under a top bar's row (56 dp: its back, Finish and rest buttons stay free) or under a tab's heading (72 dp), as small as its words; it takes no touch but its own.
        UndoBar(model, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (top == null) 72.dp else 56.dp), sheetHaze)
        WeekPill(model, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BAR_GAP + BAR_HEIGHT + BAR_GAP + Space.m))
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

/** The four tabs in the order the bar and the pager lay them out, so a swipe goes the way the bar reads. */
val TAB_ORDER = listOf(Tab.Home, Tab.Progress, Tab.Exercises, Tab.Profile)

/** The tab bar and the plus button beside it: 64 dp tall, floating 8 dp above the bottom with 16 dp at the sides and 8 dp between them. */
private val BAR_HEIGHT = 64.dp
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
        // Round 41: the release goes on at the finger's speed and eases onto the page (PagerGlide), instead of the stock snap's stall and lurch.
        val minFling = with(LocalDensity.current) { 400.dp.toPx() }
        val glide = remember(pager, minFling) { PagerGlide(pager, minFling) }
        val motion = LocalMotion.current
        // Where a tap on the bar's blink is, in ms (none at rest): the pager fades out, changes page at the midpoint and fades in, on one clock.
        val blink = remember { Animatable(BLINK_REST) }
        // The pages a tap's blink goes from and to, so Activity can stay put through a tap between Home and Progress as it does through a swipe.
        val blinkFrom = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val blinkTo = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(model.tab) {
            val page = order.indexOf(model.tab)
            if (pager.targetPage == page) return@LaunchedEffect
            if (motion) {
                blinkFrom.intValue = pager.currentPage
                blinkTo.intValue = page
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
        // Not while a finger is scrolling the page or the pager: building the next page then would be a hitch in the scroll, so it waits for the first still moment.
        // Then, at the next still moment, all four are kept, so a fast run across every tab never builds a page mid-swipe (the user,
        // round 10: "jitteryness when moving across 4 pages very quickly"). [warm] is how many pages either side are kept.
        val warm by produceState(0) {
            val still = snapshotFlow { pager.isScrollInProgress || model.tabLists.getValue(model.tab).isScrollInProgress }
            delay(800)
            still.first { !it }
            value = 1
            delay(600)
            still.first { !it }
            value = TAB_ORDER.size - 1
        }
        // The pages are what the bar and the plus button blur; nothing is recorded while a page covers the tabs.
        val haze = rememberHazeState()
        val onTabs = model.stack.isEmpty()
        // Where Activity is between Home (0) and Progress (1): the pager's place during a swipe, or during a tap's blink between the two an eased glide
        // over the whole blink (the pages fade under it; the shared copy does not), so the card stays in place whichever way the tab changes.
        val activityPos = {
            val t = blink.value
            if (t == BLINK_REST) pager.currentPage + pager.currentPageOffsetFraction
            else {
                val f = FastOutSlowInEasing.transform((t / BLINK_TOTAL).coerceIn(0f, 1f))
                blinkFrom.intValue + (blinkTo.intValue - blinkFrom.intValue) * f
            }
        }
        // While the pager is between Home and Progress, both at their top, Activity is drawn once above it (a fixed element: it does not move with the pages);
        // the pages' own copies are hidden for as long, and are the same pixels, so the hand-off at either end is not seen. Otherwise the pages' copies show.
        val homeAt = model.activityY.getValue(Tab.Home)
        val progressAt = model.activityY.getValue(Tab.Progress)
        val overlay = remember(pager, blink, model) {
            derivedStateOf {
                val pos = pager.currentPage + pager.currentPageOffsetFraction
                val tapBetween = blink.value != BLINK_REST && setOf(blinkFrom.intValue, blinkTo.intValue) == setOf(order.indexOf(Tab.Home), order.indexOf(Tab.Progress))
                // Both pages' Activity must be known and not too far apart (a page scrolled a little still uses the shared copy, which slides from one y to the other).
                val h = model.activityH.getValue(Tab.Home).floatValue
                model.stack.isEmpty() && (tapBetween || (blink.value == BLINK_REST && pos > 0.002f && pos < 0.998f)) &&
                    !homeAt.floatValue.isNaN() && !progressAt.floatValue.isNaN() && !h.isNaN() && abs(homeAt.floatValue - progressAt.floatValue) <= 0.6f * h
            }
        }
        model.activityOverlay = overlay
        val boxTop = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        // Round 32: the page's background, and the frosted cards' blur of it (the background alone is their source, so a scroll changes nothing they read).
        val backdropGround = MaterialTheme.colorScheme.background
        val backdrop = rememberBackdrop(model, backdropGround)
        val frostLook = LocalLook.current
        val frostTint = remember(backdrop.spec, frostLook, backdropGround, backdrop.photo) { glassTint(backdrop.spec, frostLook, backdropGround, backdrop.photo) }
        val frost = remember(haze, frostTint) { Frost(haze, frostTint) }
        Box(Modifier.padding(padding).fillMaxSize().onGloballyPositioned { boxTop.floatValue = it.positionInRoot().y }) {
            CompositionLocalProvider(LocalBarEnds provides ends, LocalBarInset provides inset, LocalHaze provides haze, LocalFrost provides frost) {
                // The light behind the pages is its own layer under the pager, so a tap's blink fades only the pages and the backdrop stays lit
                // (the user, round 13: no blinking background). It is a blur source too, under the pages, so glass over the pages still sees the light.
                Box(Modifier.fullBleedTop(padding.calculateTopPadding()).then(if (onTabs) Modifier.hazeSource(haze, zIndex = -1f) else Modifier).drawBackdrop(backdrop))
                HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer { alpha = blinkAlpha(blink.value); PagerWatch.pos = pager.currentPage + pager.currentPageOffsetFraction }.then(if (onTabs) Modifier.hazeSource(haze) else Modifier), beyondViewportPageCount = warm, flingBehavior = glide) { page ->
                    val ambientTop = padding.calculateTopPadding()
                    // The light stays put while pages swipe, so the page's top strip needs to know how far the page lies from the pager's left edge.
                    val shiftX: androidx.compose.ui.unit.Density.() -> Float = { (page - (pager.currentPage + pager.currentPageOffsetFraction)) * pager.layoutInfo.pageSize }
                    when (order[page]) {
                        // Under the status bar a page's content fades into the ground once it has scrolled (the Library's title and search stay put, so it has none).
                        Tab.Home -> Box(Modifier.fillMaxSize().topFadeMask(model.tabLists.getValue(Tab.Home), backdrop, ambientTop, shiftX)) { HomeTab(model) }
                        Tab.Progress -> Box(Modifier.fillMaxSize().topFadeMask(model.tabLists.getValue(Tab.Progress), backdrop, ambientTop, shiftX)) { ProgressTab(model) }
                        Tab.Exercises -> ExercisesTab(model)
                        Tab.Profile -> Box(Modifier.fillMaxSize().topFadeMask(model.tabLists.getValue(Tab.Profile), backdrop, ambientTop, shiftX)) { ProfileTab(model) }
                    }
                }
            }
            // Once the tabs are warm the overlay stays composed, hidden (not drawn, no touch, no accessibility node), so the first swipe from Home to
            // Progress does not build it in one frame; before that it is composed only while it shows.
            val showing = overlay.value
            if (showing || warm == TAB_ORDER.size - 1) Box(
                Modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = if (overlay.value) 1f else 0f }
                    .then(if (showing) Modifier else Modifier.clearAndSetSemantics {}),
            ) { Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.l)
                    .offset {
                        // Hidden, it is moved far below (clipped away), so its scroll area and switch take no touch meant for the page's own card under it
                        // (kept composed it lay over that card and swallowed its taps: the user, round 13, "tapping the grid doesn't open the calendar").
                        if (!overlay.value) return@offset IntOffset(0, 1_000_000)
                        val pos = activityPos().coerceIn(0f, 1f); val y = homeAt.floatValue + (progressAt.floatValue - homeAt.floatValue) * pos - boxTop.floatValue; IntOffset(0, if (y.isNaN()) 0 else y.roundToInt())
                    }
                    // It is only to look at: a touch that lands on it is taken, not passed to the pages it hides. Hidden, it takes none.
                    .then(if (showing) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } } else Modifier),
            ) { ActivityBody(model, Tab.Home, overlay = true) } }
            // The bar belongs to the tabs page: it goes and comes back in the same blink as the page, or under a growing card slides away with it.
            val barOut = LocalBarOut.current
            // A card that lies partly behind the bar is drawn only above the bar while it is there (see [MorphClip.barTop]), so what the bar hides stays hidden
            // from the tap on and shows as the bar leaves.
            val clipForBar = LocalMorphClip.current
            // The clock pill is in the bar: its container is not cut at the bar, and the pill itself stays while the workout grows out of it.
            val morphCard = LocalMorphCard.current
            val barSpot = remember { floatArrayOf(Float.MAX_VALUE, 0f) }
            val barSlide = with(LocalDensity.current) { 48.dp.toPx() }
            SideEffect { clipForBar?.barTop = if (morphCard == DOCK_WORKOUT) ({ Float.MAX_VALUE }) else ({ barSpot[0] + (barOut?.value ?: 0f) * (barSpot[1] + barSlide) }) }
            // While a page scrolls down the dock lowers and shortens (bar and plus together); scrolling up, or reaching the top, brings it back (the bottom does not: at the end of a page the dock stays down).
            val compact = remember { mutableStateOf(false) }
            val scrolled = model.tabLists.getValue(model.tab)
            LaunchedEffect(scrolled) {
                compact.value = false
                var last = Long.MIN_VALUE
                snapshotFlow { Triple(scrolled.firstVisibleItemIndex * 100_000L + scrolled.firstVisibleItemScrollOffset, scrolled.canScrollBackward, scrolled.canScrollForward) }.collect { (at, back, forward) ->
                    if (!back) { compact.value = false; last = at; return@collect }
                    if (last == Long.MIN_VALUE) last = at
                    val moved = at - last
                    if (abs(moved) > 24) { compact.value = moved > 0; last = at }
                }
            }
            val squeezed = animateFloatAsState(if (compact.value && motion) 1f else 0f, tween(220, easing = FastOutSlowInEasing), label = "dock squeeze")
            val squeeze = { squeezed.value }
            val lower = with(LocalDensity.current) { DOCK_LOWER.toPx() }
            // The page softens before it meets the bar: a band from clear to the page's own backdrop at 70 % (Round 36: not the flat ground, which cut a band
            // across a pattern). The backdrop is cut from the whole screen's, so the pattern runs on through the band.
            val fadeHeight = with(LocalDensity.current) { (96.dp + nav).toPx() }
            val fadeTop = with(LocalDensity.current) { padding.calculateTopPadding().toPx() }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 1f - (barOut?.value ?: 0f).coerceIn(0f, 1f) }
                    .drawBehind {
                        val strip = androidx.compose.ui.geometry.Rect(0f, size.height - fadeHeight, size.width, size.height)
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(strip, androidx.compose.ui.graphics.Paint())
                            inset(0f, -fadeTop, 0f, 0f) { backdrop.draw(this, density) }
                            drawRect(
                                Brush.verticalGradient(0f to ComposeColor.Transparent, 1f to ComposeColor.Black.copy(alpha = 0.7f), startY = strip.top, endY = strip.bottom),
                                strip.topLeft, strip.size, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                            canvas.restore()
                        }
                    },
            )
            // Below the dock the page is blurred too, fading out toward the top so there is no edge (not with solid glass).
            if (!LocalSolidGlass.current) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(nav + BAR_GAP + BAR_HEIGHT / 2)
                    .graphicsLayer { alpha = 1f - (barOut?.value ?: 0f).coerceIn(0f, 1f) }
                    .hazeEffect(haze, GlassBlurStyle) { GlassBlurBlock(); progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f) },
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.l).padding(bottom = BAR_GAP + nav)
                    .onGloballyPositioned { barSpot[0] = it.positionInRoot().y; barSpot[1] = it.size.height.toFloat() }
                    .graphicsLayer { translationY = squeezed.value * lower },
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP), verticalAlignment = Alignment.CenterVertically,
            ) {
              // The bar and the plus blur the pages (this part of the tree is outside the pager's own provider).
              CompositionLocalProvider(LocalHaze provides haze) {
                val plus: @Composable () -> Unit = {
                    // The clock pill stays where it is while the workout grows out of it; for every other card it slides away with the bar.
                    PlusButton(model, squeeze, Modifier.graphicsLayer { if (morphCard != DOCK_WORKOUT) translationY = (barOut?.value ?: 0f) * (size.height + 48.dp.toPx()) })
                }
                // A2: a left-handed dock puts the plus on the left of the bar.
                if (model.flag("dock.left", false)) plus()
                TabBar(
                    model, order, Modifier.weight(1f).graphicsLayer { translationY = (barOut?.value ?: 0f) * (size.height + 48.dp.toPx()) },
                    // A swipe moves the circle with the finger; a tap's blink glides it on its own clock.
                    // Only a sideways move of the pages counts: a vertical scroll or fling inside a tab can mark the pager as scrolling
                    // (its nested-scroll settle) without moving it, and must not bring the capsule up.
                    follow = { if (pager.isScrollInProgress && abs(pager.currentPageOffsetFraction) > 0.001f && blink.value == BLINK_REST) pager.currentPage + pager.currentPageOffsetFraction else null },
                    squeeze = squeeze,
                )
                if (!model.flag("dock.left", false)) plus()
              }
            }
        }
    }
}

/**
 * The four tabs as one glass bar with round ends over the page, darker and tinted with the accent (a 1 dp accent outline, the page blurred
 * behind it on Android 12 and up): an outline icon in each slot, centred, with no word at rest (each slot still speaks its tab's name). The
 * current tab's icon is in the accent. There is no highlight at rest: while the bar moves between tabs (a tap's glide, or a swipe) a capsule the
 * full height of the bar stretches from the old slot to the new one, fading in as the move starts and out as it settles. [current] is the tab
 * drawn as selected; Settings' live preview draws the bar with one that is not the app's own, under a layer that takes no touch. [squeeze]
 * (0 to 1, read when measuring) shortens the bar while a page scrolls down.
 */
@Composable
fun TabBar(model: AppModel, order: List<Tab>, modifier: Modifier, current: Tab = model.tab, follow: () -> Float? = { null }, squeeze: () -> Float = { 0f }) {
    // One capsule glides over the slots (Motion.pill) from the old tab to the new, its leading edge first; or it follows a swipe (follow, in tabs).
    // A finger on the dock itself (round 29): where it has the indicator, in tabs (NaN when none), read in the draw phase.
    val finger = remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) }
    val slide: () -> Float? = { finger.floatValue.takeIf { !it.isNaN() } ?: follow() }
    // Round 31: while a finger slides along the dock only the dock moves. [live] is the tab the slide is over (the indicator, the name and the tick follow it); the page
    // and the app's tab stay as they were until the finger lifts, then the tab changes once, as a tap on it would change it. null when no slide is under way.
    val live = remember { mutableStateOf<Tab?>(null) }
    val shownTab = live.value ?: current
    val span = rememberStretch(order.indexOf(shownTab).coerceAtLeast(0), slide)
    // The capsule stays up while the bar is swiped or glides and for CAPSULE_HOLD ms after, then fades over CAPSULE_FADE ms; a new move shows it again.
    val motion = LocalMotion.current
    val shown = remember { Animatable(0f) }
    val dragging by remember { derivedStateOf { slide() != null } }
    val started = remember { booleanArrayOf(false) }
    LaunchedEffect(shownTab, dragging) {
        if (!started[0] && !dragging) { started[0] = true; return@LaunchedEffect }
        started[0] = true
        shown.snapTo(1f)
        if (dragging) return@LaunchedEffect
        delay(CAPSULE_HOLD)
        if (motion) shown.animateTo(0f, tween(CAPSULE_FADE, easing = LinearEasing)) else shown.snapTo(0f)
    }
    // The name of the tab just switched to shows under its icon for NAME_HOLD ms, then fades (never at the app's start; nothing with animations off).
    // While a finger is on the dock it stays up, and the hold starts when the finger lifts.
    val flash = remember { mutableStateOf<Tab?>(null) }
    val begun = remember { booleanArrayOf(false) }
    val touching by remember { derivedStateOf { !finger.floatValue.isNaN() } }
    LaunchedEffect(shownTab) {
        if (!begun[0]) { begun[0] = true; return@LaunchedEffect }
        flash.value = if (motion) shownTab else null
    }
    LaunchedEffect(flash.value, touching) {
        if (flash.value == null || touching) return@LaunchedEffect
        delay(NAME_HOLD)
        flash.value = null
    }
    val haptic = rememberHaptic()
    val look = LocalLook.current
    val dark = look.dark
    val capsule = if (dark) ComposeColor.White.copy(alpha = 0.12f) else ComposeColor.Black.copy(alpha = 0.07f)
    Row(
        modifier.dockHeight(squeeze).glass(PillShape, accent = look.orange).padding(Space.xs)
            .drawBehind {
                val s = span()
                val slot = size.width / order.size
                val gap = 2.dp.toPx()
                val left = s.left { it * slot + gap }
                val right = s.right { (it + 1) * slot - gap }
                PagerWatch.capsule = (left + right) / 2f / slot - 0.5f
                // How far an edge is from lying on a slot's edge, in slots: 0 at rest, so nothing is drawn; it fades in over the first
                // 12 % of a slot's travel and out over the last (about 150 ms each at the glide's pace). With animations off the span is a cut.
                val l = s.left { it.toFloat() }
                val r = s.right { it + 1f }
                val away = max(abs(l - roundHalfUp(l)), abs(r - roundHalfUp(r)))
                val a = max((away / 0.12f).coerceIn(0f, 1f), shown.value)
                if (a > 0f) {
                    val box = Size(right - left, size.height)
                    val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    drawRoundRect(capsule.copy(alpha = capsule.alpha * a), androidx.compose.ui.geometry.Offset(left, 0f), box, corner)
                    if (dark) {
                        val line = 1.dp.toPx()
                        drawRoundRect(
                            Brush.verticalGradient(0f to ComposeColor.White.copy(alpha = 0.22f * a), 0.45f to ComposeColor.Transparent, startY = 0f, endY = size.height),
                            androidx.compose.ui.geometry.Offset(left + line / 2f, line / 2f), Size(box.width - line, box.height - line),
                            androidx.compose.ui.geometry.CornerRadius((size.height - line) / 2f), style = Stroke(line),
                        )
                    }
                }
            }
            // A slide along the dock is relative (round 30): it starts from the tab that was current, not the slot under the finger. The selection is that tab
            // plus the drag in slot widths, rounded and clamped, so nothing changes before half a slot, and the indicator follows the same position. A flick moves
            // one tab from the start. A tap is left to the slots; a vertical start does nothing. Round 31: nothing but the dock moves until the finger lifts; then the
            // app's tab changes once, by the same assignment a tap makes, so the page changes as it does for a tap (the blink, Activity's card and all).
            .slideAlong(
                order, order.size, { order.indexOf(model.tab).coerceAtLeast(0) }, { size.width / order.size.toFloat() },
                onFinger = { finger.floatValue = it }, onStep = { live.value = order[it] },
                onEnd = { if (model.tab != order[it]) model.tab = order[it]; live.value = null },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((i, tab) in order.withIndex()) TabItem(model, tab, shownTab == tab, i, span, flash.value == tab)
    }
}

/** How long a tab's name stays fully up under its icon, and how long it fades in and out (ms); how far the icon rises meanwhile; the speed (dp per second) that makes a slide a flick. */
private const val NAME_HOLD = 900L
private const val NAME_IN = 100
private const val NAME_FADE = 300
private val NAME_LIFT = 7.dp

private const val CAPSULE_HOLD = 1500L
private const val CAPSULE_FADE = 400

/** The dock's height: [BAR_HEIGHT], less up to [DOCK_SHRINK] of it by [squeeze] (read when measuring, so a scroll does not recompose the bar). */
private fun Modifier.dockHeight(squeeze: () -> Float): Modifier = layout { measurable, constraints ->
    val h = (BAR_HEIGHT.roundToPx() * (1f - DOCK_SHRINK * squeeze().coerceIn(0f, 1f))).roundToInt()
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h, minWidth = if (constraints.minWidth == 0) h else constraints.minWidth))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** While a tab page scrolls down the dock lowers by [DOCK_LOWER] and its height shrinks by [DOCK_SHRINK]; scrolling up brings it back. */
private val DOCK_LOWER = 8.dp
private const val DOCK_SHRINK = 0.12f

/** One slot of the bar, the same width for every tab, its icon centred. Role Tab with the selected state and [spoken] as its name. */
@Composable
private fun RowScope.TabSlot(selected: Boolean, spoken: String, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    // No ripple: it filled the whole round-ended slot and read as a second, oval indicator beside the capsule (the user's word, 2026-11-18).
    // A press dips the icon a little instead.
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (down && LocalMotion.current) 0.9f else 1f, Motion.pressNow(), label = "tab press")
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .clickable(source, indication = null, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = spoken }
            .graphicsLayer { scaleX = press; scaleY = press },
        contentAlignment = Alignment.Center, content = content,
    )
}

/** One tab: its outline icon (27 dp), with no word. It takes the accent as the gliding capsule reaches it and gives it back as the capsule leaves. */
@Composable
private fun RowScope.TabItem(model: AppModel, tab: Tab, selected: Boolean, index: Int, span: () -> Span, naming: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val quiet = scheme.onSurfaceVariant
    val lit = LocalLook.current.orange
    val icon = when (tab) {
        Tab.Home -> CleanIcons.DockHome
        Tab.Progress -> CleanIcons.DockProgress
        Tab.Exercises -> CleanIcons.DockLibrary
        Tab.Profile -> CleanIcons.DockProfile
    }
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    // E6's arrival disc behind a tab's icon was removed in Round 41 at the user's request (I-170).
    // D17: a tap on the current tab's icon scrolls its list to the top, and the icon nods (2 dp down and back).
    val nod = remember { Animatable(0f) }
    val nodPx = with(LocalDensity.current) { 2.dp.toPx() }
    // The name under the icon: in over NAME_IN ms, out over NAME_FADE; the icon rises by NAME_LIFT while it shows and settles as it fades.
    val name by animateFloatAsState(if (naming) 1f else 0f, tween(if (naming) NAME_IN else NAME_FADE, easing = LinearEasing), label = "tab name")
    val liftPx = with(LocalDensity.current) { NAME_LIFT.toPx() }
    TabSlot(selected, tab.label, {
        if (!selected) model.tab = tab
        else scope.launch {
            val list = model.tabLists.getValue(tab)
            if (!motion) { list.scrollToItem(0); return@launch }
            launch { nod.animateTo(1f, tween(110)); nod.animateTo(0f, tween(140)) }
            list.animateScrollToItem(0)
        }
    }) {
        Icon(
            icon, null,
            Modifier.size(27.dp)
                .graphicsLayer { translationY = nod.value * nodPx - name * liftPx }
                // The icon's colour follows the gliding capsule in the draw phase (a swipe or a glide recomposes no tab): drawn black, then dyed.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent { drawContent(); drawRect(androidx.compose.ui.graphics.lerp(quiet, lit, span().cover(index)), blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn) },
            tint = ComposeColor.Black,
        )
        // The slot already speaks the name (its content description), so this is for the eye only; the tag lets a test find it.
        if (name > 0.001f) Text(
            tab.label,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).graphicsLayer { alpha = name }.clearAndSetSemantics { testTagSemantics = "dock name ${tab.label}" },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold), color = scheme.onSurface, maxLines = 1,
        )
    }
}

/**
 * The round button beside the bar, as tall as it, filled with the accent, its plus in the theme's ink, with a soft accent shadow: it raises the Workout sheet. With a workout running it is a wider pill
 * with a pulsing accent dot, the workout's name and its clock (or the rest counting down), and a tap goes back to the workout.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlusButton(model: AppModel, squeeze: () -> Float, modifier: Modifier = Modifier) {
    val draft = model.draft
    // With a workout running the pill is a card of the tabs page: the workout grows out of it, its name and clock travelling (CardMorph.kt).
    CompositionLocalProvider(LocalCardKey provides if (draft != null) DOCK_WORKOUT else null) {
    val open = cardTap { if (draft != null) model.resumeWorkout() else model.workoutSheet = true }
    // Round 30: a hold on the running workout's pill: a peek of the workout above the choices (the workout page's own buttons and rest options, not copies of them).
    val workouts by model.workouts.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val holdActions: (() -> List<HoldAction>)? = if (draft == null) null else ({
        buildList {
            add(HoldAction("Resume", CleanIcons.Play) { open() })
            add(HoldAction("Add exercise", CleanIcons.Add) { adding = true })
            if (model.restEnd != null) {
                add(HoldAction("Skip rest", CleanIcons.Forward) { model.startRest(0) })
                add(HoldAction("Add 30 s", CleanIcons.TimerLine) { model.changeRest(30) })
            }
            add(HoldAction("Finish workout", CleanIcons.Check) { model.resumeWorkout("finish") })
            add(HoldAction("Cancel workout", CleanIcons.Delete, danger = true) { model.resumeWorkout("cancel") })
        }
    })
    val holdPeek: (() -> List<String>?)? = if (draft == null) null else ({ pillPeek(model.draft) })
    val motion = LocalMotion.current
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down && motion) 0.94f else 1f, Motion.pressNow(), label = "press")
    // E19: when a workout ends and the pill folds back into the plus, the plus springs once (1 to 1.08 and back).
    val bump = remember { Animatable(1f) }
    var hadWorkout by remember { mutableStateOf(draft != null) }
    LaunchedEffect(draft != null) {
        val ended = hadWorkout && draft == null
        hadWorkout = draft != null
        if (ended && motion) {
            kotlinx.coroutines.delay(Motion.PILL * 2L / 3)
            bump.animateTo(Motion.PLUS_BUMP, Motion.plusUp())
            bump.animateTo(1f, Motion.plusBack())
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val look = LocalLook.current
    // D4: while a sheet opened from the plus is up, the plus is turned an eighth of a turn into an ×.
    val turn by animateFloatAsState(if (model.workoutSheet || model.clash != null) 45f else 0f, if (motion) tween(220, easing = FastOutSlowInEasing) else snap(), label = "plus turn")
    // The accent fill, which gives way to the glass as the plus grows into the clock pill (and comes back as it folds).
    val fill by animateFloatAsState(if (draft == null) 1f else 0f, if (motion) Motion.quick() else snap(), label = "plusFill")
    // Spoken: what a tap does, with the workout's name and how long it has run.
    val minutes = draft?.let { (nowMillis() - it.startMillis) / 60_000 }
    Box(
        modifier.graphicsLayer { scaleX = scale * bump.value; scaleY = scale * bump.value }.dockHeight(squeeze)
            .opensInto(null, MaterialTheme.colorScheme.surfaceContainer, round = true)
            .shadow(12.dp, CircleShape, clip = false, ambientColor = look.orange.copy(alpha = 0.25f * fill), spotColor = look.orange.copy(alpha = 0.50f * fill))
            .glass(CircleShape, lift = 14.dp * (1f - fill))
            .drawBehind { if (fill > 0f) drawOutline(CircleShape.createOutline(size, layoutDirection, this), look.orange.copy(alpha = fill)) }
            .clickable(source, LocalIndication.current, role = Role.Button, onClick = open)
            .holdMenu(CircleShape, holdActions, fill = MaterialTheme.colorScheme.surfaceContainer, peek = holdPeek)
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
                Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) { Icon(CleanIcons.Add, null, Modifier.size(28.dp).graphicsLayer { rotationZ = turn }, tint = look.ink) }
            } else {
                val now = nowMillis()
                val rest = model.restEnd?.let { (it - now) / 1000 }?.takeIf { it > 0 }
                // Only while the tabs are in view: under a page the dot cannot be seen and the endless clock would wake the screen every frame.
                val pulse = remember { Animatable(1f) }
                val seen = model.stack.isEmpty() && flourishOn()
                LaunchedEffect(seen) { if (seen) pulse.animateTo(0.3f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse)) else pulse.snapTo(1f) }
                // The rest counts down as a thin ring round the dot: full at the rest's start, empty at its end (none when no rest runs).
                val end = model.restEnd
                val ringTarget = if (rest != null && end != null && model.restTotal > 0) ((end - now) / (model.restTotal * 1000f)).coerceIn(0f, 1f) else 0f
                val ringLeft by animateFloatAsState(ringTarget, if (motion) Motion.clock() else snap(), label = "rest ring")
                Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(18.dp).drawBehind {
                            if (ringTarget > 0f || ringLeft > 0.001f) {
                                val w = 2.dp.toPx()
                                drawArc(look.orange, -90f, 360f * ringLeft, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(8.dp).graphicsLayer { alpha = if (motion) pulse.value else 1f }.background(LocalLook.current.orange, CircleShape)) }
                    // The name and the clock travel into the workout page's (and back), as a card's label does.
                    if (rest == null) MorphText(
                        androidx.compose.ui.text.AnnotatedString(shown.name), "name",
                        // D14: a name longer than the pill's room scrolls slowly (after a pause) instead of being cut off.
                        Modifier.widthIn(max = 96.dp).then(if (motion) Modifier.basicMarquee(initialDelayMillis = 1500, velocity = 16.dp) else Modifier),
                        style = MaterialTheme.typography.labelLarge, color = ink,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = if (motion) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                    MorphText(
                        androidx.compose.ui.text.AnnotatedString(if (rest != null) "Rest ${clock(rest)}" else clock((now - shown.startMillis) / 1000)), "clock",
                        style = MaterialTheme.typography.labelLarge, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
    if (adding) ExercisePicker(
        model, workouts, onPick = { model.addExercise(it); adding = false }, onDismiss = { adding = false },
        onPickMany = { names -> names.forEach { model.addExercise(it) }; adding = false },
        title = "Add exercise", onPickNext = { model.addExercise(it, afterCurrent = true); adding = false },
    )
    }
}

/** How far the Workout sheet is up, 0 (closed) to 1 (open); it follows [AppModel.workoutSheet]. */
@Composable
fun rememberWorkoutSheetProgress(model: AppModel, motion: Boolean): androidx.compose.runtime.State<Float> {
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

/** The small glass pill above the dock on the first open of a week, "Last week: 3 of 3 ✓": it fades away after about four seconds, or on a tap. Only over the tabs. */
@Composable
private fun WeekPill(model: AppModel, modifier: Modifier) {
    val text = model.weekPill
    val motion = LocalMotion.current
    LaunchedEffect(text) { if (text != null) { delay(4000); model.weekPill = null } }
    // The words stay while the pill fades out.
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    androidx.compose.animation.AnimatedVisibility(
        text != null && model.stack.isEmpty() && !model.welcoming, modifier,
        enter = if (motion) androidx.compose.animation.fadeIn(tween(200)) else androidx.compose.animation.EnterTransition.None,
        exit = if (motion) androidx.compose.animation.fadeOut(tween(400)) else androidx.compose.animation.ExitTransition.None,
    ) {
        androidx.compose.material3.Text(
            last, Modifier.glass(PillShape, accent = LocalLook.current.orange).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { model.weekPill = null }
                .padding(horizontal = Space.l, vertical = Space.s),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The head of the hold menu on the running workout's pill: its name, how long it has run and sets done of all, and the exercise being done now. */
internal fun pillPeek(draft: Draft?): List<String>? {
    val d = draft ?: return null
    val sets = d.blocks.flatMap { it.items }.filterIsInstance<DraftSet>()
    val now = nextSetOf(d)?.exercise ?: d.blocks.lastOrNull()?.exercise
    return listOfNotNull(d.name, "${clock((currentMillis() - d.startMillis) / 1000)} · ${sets.count { it.done }} of ${sets.size} sets", now?.let { "Now: $it" })
}

/** Test hook: where the tabs pager stood when it was last drawn (page + fraction), and where the dock's capsule was drawn (its centre, in slots). */
object PagerWatch { var pos = 0f; var capsule = 0f }
