package clean.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few icons the app needs beyond the small set that ships with Compose. The outlines are
 * Google's Material icons (Apache License 2.0, credited in NOTICE.md), kept here as path data so
 * the full icon library, which is very large, does not have to be bundled.
 */
object CleanIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

    /** A line icon: strokes of [LINE] width in a 24 dp box with round ends, no fill; the tab bar's own set, drawn here. */
    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(addPathNodes(p), stroke = SolidColor(Color.Black), strokeLineWidth = LINE, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()

    /** A dock icon as drawn in the icon sheets (24 dp box, stroke [width], the given cap and join), converted from the SVG paths. */
    private fun dock(name: String, width: Float, cap: StrokeCap, join: StrokeJoin, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(addPathNodes(p), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = cap, strokeLineJoin = join)
        }.build()

    /** The dock's four (round 4): a rounded house, axes with a rising line, a row of books, a person in a circle. */
    val DockHome = dock("DockHome", 2f, StrokeCap.Round, StrokeJoin.Round, "M4 10.5 12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5H15v-5.5h-6v5.5H5.5A1.5 1.5 0 0 1 4 19z")
    val DockProgress = dock("DockProgress", 2.4f, StrokeCap.Square, StrokeJoin.Miter, "M4 4v16h16", "M8 15l3.5-4 3 2.5L20 7")
    val DockLibrary = dock("DockLibrary", 2f, StrokeCap.Round, StrokeJoin.Round, "M5 4.5h3.5v15H5zM10.5 4.5H14v15h-3.5z", "M14.2 5.4l3.3-.9 3 14.4-3.3.9z")
    val DockProfile = dock("DockProfile", 2f, StrokeCap.Round, StrokeJoin.Round, "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0", "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", "M6.8 18.2c1.2-2.2 3-3.2 5.2-3.2s4 1 5.2 3.2")

    private const val LINE = 1.4f

    /** The tab bar's icons: a house, a line rising along its axes, a list with bullets, a person. */
    val TabHome = line("TabHome", "M3.5 11 L12 3.8 L20.5 11", "M5.8 9.4 V19.8 H18.2 V9.4", "M10 19.8 V14.2 H14 V19.8")
    val TabProgress = line("TabProgress", "M4 4 V20 H20", "M7.5 15.5 L11 11.5 L14 13.8 L19 7.5", "M15.5 7.5 H19 V11")
    val TabList = line("TabList", "M9.5 7 H20", "M9.5 12 H20", "M9.5 17 H20", "M4.4 7 H4.6", "M4.4 12 H4.6", "M4.4 17 H4.6")
    val TabPerson = line("TabPerson", "M12 4.5 a3.6 3.6 0 1 0 0 7.2 a3.6 3.6 0 1 0 0 -7.2", "M5 20 C5 16.2 8 14.2 12 14.2 C16 14.2 19 16.2 19 20")

    val History = icon("History", "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z")
    val Body = icon("Body", "M20.5 6c-2.61.7-5.67 1-8.5 1s-5.89-.3-8.5-1L3 8c1.86.5 4 .83 6 1v13h2v-6h2v6h2V9c2-.17 4.14-.5 6-1l-.5-2zM12 6c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z")
    val Swap = icon("Swap", "M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z")
    val LeftRight = icon("LeftRight", "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z")
    val Up = icon("Up", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z")
    val Down = icon("Down", "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z")
    val Link = icon("Link", "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z")
    val Pin = icon("Pin", "M16 9V4h1c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z")
    val Notes = icon("Notes", "M3 18h12v-2H3v2zM3 6v2h18V6H3zm0 7h18v-2H3v2z")
    val Folder = icon("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    val Copy = icon("Copy", "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z")
    val Star = icon("Star", "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
    val Eye = icon("Eye", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z")
    val EyeOff = icon("EyeOff", "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z")
    val Grip = icon("Grip", "M20 9H4v2h16V9zM4 15h16v-2H4v2z")
    val Bolt = icon("Bolt", "M11 21h-1l1-7H7.5c-.88 0-.33-.75-.31-.78C8.48 10.94 10.42 7.54 13 3h1l-1 7h3.51c.4 0 .62.19.4.66C12.97 17.55 11 21 11 21z")

    /**
     * Line versions of the Material icons the app used filled (2026-10-18, round 11): the same 24 dp box and stroke as the tab bar's,
     * so every icon in the app is drawn the same way. Drawn here, not taken from the icon library.
     */
    val Close = line("Close", "M6.5 6.5 L17.5 17.5", "M17.5 6.5 L6.5 17.5")
    val Add = line("Add", "M12 5 V19", "M5 12 H19")
    val Check = line("Check", "M5 12.5 L10 17.5 L19 7")
    val More = line("More", "M11.1 5.5 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0", "M11.1 12 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0", "M11.1 18.5 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0")
    val Delete = line("Delete", "M4.5 7 H19.5", "M9.5 7 V4.8 H14.5 V7", "M6.5 7 L7.4 19.5 H16.6 L17.5 7", "M10.2 10.5 V16", "M13.8 10.5 V16")
    val Share = line("Share", "M15.7 5.5 a2.3 2.3 0 1 0 4.6 0 a2.3 2.3 0 1 0 -4.6 0", "M3.7 12 a2.3 2.3 0 1 0 4.6 0 a2.3 2.3 0 1 0 -4.6 0", "M15.7 18.5 a2.3 2.3 0 1 0 4.6 0 a2.3 2.3 0 1 0 -4.6 0", "M8.1 10.9 L15.9 6.6", "M8.1 13.1 L15.9 17.4")
    val Back = line("Back", "M19 12 H5.5", "M11 6 L5 12 L11 18")
    val Forward = line("Forward", "M5 12 H18.5", "M13 6 L19 12 L13 18")
    val ChevronDown = line("ChevronDown", "M6.5 9.5 L12 15 L17.5 9.5")
    val ChevronUp = line("ChevronUp", "M6.5 14.5 L12 9 L17.5 14.5")
    val ChevronRight = line("ChevronRight", "M9.5 6.5 L15 12 L9.5 17.5")
    val ChevronLeft = line("ChevronLeft", "M14.5 6.5 L9 12 L14.5 17.5")
    val DropDown = line("DropDown", "M7.5 10 L12 14.5 L16.5 10 Z")
    val Search = line("Search", "M4.7 10.5 a5.8 5.8 0 1 0 11.6 0 a5.8 5.8 0 1 0 -11.6 0", "M14.8 14.8 L19.8 19.8")
    val Play = line("Play", "M8 5.5 V18.5 L18.5 12 Z")
    val Edit = line("Edit", "M4.5 19.5 L5.3 15.6 L15.6 5.3 a1.8 1.8 0 0 1 2.6 0 l0.5 0.5 a1.8 1.8 0 0 1 0 2.6 L8.4 18.7 Z", "M13.8 7.1 L16.9 10.2")
    val Calendar = line("Calendar", "M4.5 6.5 H19.5 V19.5 H4.5 Z", "M4.5 10.5 H19.5", "M8.5 4 V8", "M15.5 4 V8")
    val Settings = line("Settings", "M8.8 12 a3.2 3.2 0 1 0 6.4 0 a3.2 3.2 0 1 0 -6.4 0", "M5.2 12 a6.8 6.8 0 1 0 13.6 0 a6.8 6.8 0 1 0 -13.6 0", "M12 5.2 V3", "M12 18.8 V21", "M5.2 12 H3", "M18.8 12 H21", "M16.81 7.19 L18.36 5.64", "M7.19 7.19 L5.64 5.64", "M16.81 16.81 L18.36 18.36", "M7.19 16.81 L5.64 18.36")
    val Refresh = line("Refresh", "M19 12 A7 7 0 1 1 16.95 7.05", "M17.2 3.4 L17.1 7.2 L13.3 7.1")
    /** Two arrows chasing each other: pick another. */
    val Cycle = line("Cycle", "M4.5 10 V9 a3.5 3.5 0 0 1 3.5 -3.5 H18.5", "M15.5 2.5 L18.5 5.5 L15.5 8.5", "M19.5 14 V15 a3.5 3.5 0 0 1 -3.5 3.5 H5.5", "M8.5 15.5 L5.5 18.5 L8.5 21.5")
    val Info = line("Info", "M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0", "M12 11 V16.5", "M11.1 7.9 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0")
    val Face = line("Face", "M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0", "M8.3 10 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0", "M13.9 10 a0.9 0.9 0 1 0 1.8 0 a0.9 0.9 0 1 0 -1.8 0", "M8.8 14.2 Q12 17 15.2 14.2")
    val StarLine = line("StarLine", "M12 3.8 L14.5 9 L20.2 9.6 L15.9 13.4 L17.1 19 L12 16.1 L6.9 19 L8.1 13.4 L3.8 9.6 L9.5 9 Z")
    val ShieldLine = line("ShieldLine", "M12 3.2 L19.2 6.2 V11.6 C19.2 15.8 16.2 19.4 12 20.8 C7.8 19.4 4.8 15.8 4.8 11.6 V6.2 Z")
    val TimerLine = line("TimerLine", "M12 7 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14", "M12 10 V14.2 L14.4 15.6", "M9.6 3.5 H14.4")
    val DumbbellLine = line("DumbbellLine", "M2.8 10 V14", "M5.5 7.5 H8 V16.5 H5.5 Z", "M8 12 H16", "M16 7.5 H18.5 V16.5 H16 Z", "M21.2 10 V14")
    // Round 37: the Settings main page's icons, drawn after the mockup (artifacts/round6/settings-studio.html).
    val ChartLine = line("ChartLine", "M4 4 V20 H20", "M8 15 L11.5 11 L14.5 13.5 L20 7")
    val LookLine = line("LookLine", "M4 12 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0", "M12 4 V20", "M12 4 a8 8 0 0 1 0 16 z")
    val SoundLine = line("SoundLine", "M9 18 V6 L19 4 V16", "M4 18 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0", "M14 16 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0")
    val LockLine = line("LockLine", "M7 11 H17 a2 2 0 0 1 2 2 V18 a2 2 0 0 1 -2 2 H7 a2 2 0 0 1 -2 -2 V13 a2 2 0 0 1 2 -2 z", "M8 11 V8 a4 4 0 0 1 8 0 V11")
    val TransferLine = line("TransferLine", "M4 8 H18 L15 5", "M20 16 H6 L9 19")
    val BooksLine = line("BooksLine", "M5 4.5 H8.5 V19.5 H5 z", "M10.5 4.5 H14 V19.5 H10.5 z", "M14.2 5.4 L17.5 4.5 L20.5 18.9 L17.2 19.8 z")
    val Person = TabPerson
}
