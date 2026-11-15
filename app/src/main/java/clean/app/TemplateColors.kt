package clean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalLook
import clean.core.NO_TEMPLATE_COLOR
import clean.core.TEMPLATE_COLOR_COUNT
import clean.core.TEMPLATE_HUES
import clean.core.TemplateColorMode
import clean.core.TemplateColorScope
import clean.core.parseTemplateColor
import clean.core.parseTemplateColorScope
import clean.core.templateColorModeOf

/*
 * Template colours (docs/customization.md, section 5): the colour a template carries, shown as a dot on the Today card, History
 * rows and the activity calendar. Off hides every dot but keeps what is stored; Random gives each template in scope a colour
 * once (AppModel); Manual lets the user choose, from a template's menu.
 */

/** What each of the ten colours is called, for the screen reader and the swatches. */
val TEMPLATE_COLOR_NAMES = listOf("Red", "Orange", "Amber", "Lime", "Green", "Teal", "Cyan", "Blue", "Violet", "Pink")

/** The colours templates show for the active profile: [mode], and the visible colour of each template by id and by name. */
data class TemplateColors(val mode: TemplateColorMode, private val byId: Map<Long, Int>, private val byName: Map<String, Int>) {
    val on: Boolean get() = mode != TemplateColorMode.OFF
    fun ofId(id: Long): Int? = byId[id]
    /** A workout belongs to the template of exactly its name, as the Workout tab's "last done" does. */
    fun ofName(name: String): Int? = byName[name]

    companion object {
        val OFF = TemplateColors(TemplateColorMode.OFF, emptyMap(), emptyMap())

        fun of(options: Map<String, String>, templates: List<Template>, profile: Long?): TemplateColors {
            if (profile == null) return OFF
            val mode = templateColorModeOf(options["tpl.colors:$profile"])
            if (mode == TemplateColorMode.OFF) return OFF
            val byId = HashMap<Long, Int>()
            val byName = LinkedHashMap<String, Int>()
            for (t in templates) {
                val colour = parseTemplateColor(options["tpl.color:$profile:${t.id}"])?.takeIf { it != NO_TEMPLATE_COLOR } ?: continue
                byId[t.id] = colour
                byName.putIfAbsent(t.name, colour)
            }
            return TemplateColors(mode, byId, byName)
        }
    }
}

/** One of the ten colours on the current background: lighter on dark pages, deeper on light ones, and a grey step of the ink in Mono. */
@Composable
fun templateColor(index: Int): Color {
    val look = LocalLook.current
    val i = index.coerceIn(0, TEMPLATE_COLOR_COUNT - 1)
    if (look.mono) return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f + 0.7f * i / (TEMPLATE_COLOR_COUNT - 1))
    return Color.hsl(TEMPLATE_HUES[i].toFloat(), 0.62f, if (look.dark) 0.66f else 0.42f)
}

/** A small round mark in a template's colour, spoken as `Template colour blue`. */
@Composable
fun TemplateDot(index: Int, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier.size(size).background(templateColor(index), CircleShape)
            .semantics { contentDescription = "Template colour ${TEMPLATE_COLOR_NAMES[index].lowercase()}" },
    )
}

/** The menu of a template (Manual mode): the ten colours and none. */
@Composable
fun TemplateColorSheet(model: AppModel, template: Template, onDismiss: () -> Unit) {
    val colors by model.templateColors.collectAsState()
    val current = colors.ofId(template.id)
    Sheet(onDismiss, "Colour of ${template.name}") {
        for (rowStart in 0 until TEMPLATE_COLOR_COUNT step 5) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.m), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in rowStart until rowStart + 5) {
                    val picked = current == i
                    Box(
                        Modifier.size(52.dp).selectable(picked, role = Role.RadioButton, onClick = { model.setTemplateColor(template.id, i); onDismiss() })
                            .semantics { contentDescription = TEMPLATE_COLOR_NAMES[i] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(36.dp).background(templateColor(i), CircleShape).then(if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (picked) Icon(CleanIcons.Check, null, Modifier.size(18.dp), tint = templateColor(i).readableOn())
                        }
                    }
                }
            }
        }
        SheetGap()
        SheetRow("None", value = if (current == null) "Chosen" else "") { model.setTemplateColor(template.id, null); onDismiss() }
    }
}

/** Settings, Workout, "Template colours": Off, Random with its scope, or Manual. */
@Composable
fun TemplateColorSettings(model: AppModel) {
    val stored by model.options.collectAsState()
    val profile by model.activeId.collectAsState()
    val templates by model.templates.collectAsState()
    val id = profile ?: return
    val mode = templateColorModeOf(stored["tpl.colors:$id"])
    val scope = parseTemplateColorScope(stored["tpl.colors.scope:$id"])
    Section("Template colours") {
        Text("A small coloured dot by a template's name on the Today card, on History, and under a day on the activity calendar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Choice(TemplateColorMode.entries.toList(), mode, { it.label }) { model.setOption("tpl.colors:$id", it.key) }
        when (mode) {
            TemplateColorMode.OFF -> Text("Colours you have set stay stored while this is off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TemplateColorMode.MANUAL -> Text("Choose a template's colour from its menu on the Workout tab, Colour. A template may have none.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TemplateColorMode.RANDOM -> {
                Text("Each template gets a colour that has been used the least so far, never the one nearest the app's accent. It is kept once given.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Colour", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val kinds = listOf(TemplateColorScope.Kind.ALL to "All templates", TemplateColorScope.Kind.FOLDERS to "Chosen folders", TemplateColorScope.Kind.SOME to "Chosen templates")
                Choice(kinds, kinds.first { it.first == scope.kind }, { it.second }) { (kind, _) ->
                    model.setOption("tpl.colors.scope:$id", TemplateColorScope(kind, if (kind == scope.kind) scope.items else emptyList()).encode())
                }
                fun toggle(item: String, on: Boolean) {
                    val items = if (on) scope.items + item else scope.items - item
                    model.setOption("tpl.colors.scope:$id", TemplateColorScope(scope.kind, items.distinct()).encode())
                }
                when (scope.kind) {
                    TemplateColorScope.Kind.ALL -> {}
                    TemplateColorScope.Kind.FOLDERS -> {
                        val folders = templates.map { it.folder }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()
                        if (folders.isEmpty()) Text("No template is in a folder yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (f in folders) CheckRow(f, scope.items.any { it.equals(f, ignoreCase = true) }) { on -> toggle(f, on) }
                    }
                    TemplateColorScope.Kind.SOME -> {
                        if (templates.isEmpty()) Text("There are no templates yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (t in templates.sortedBy { it.name.lowercase() }) CheckRow(t.name, t.id.toString() in scope.items) { on -> toggle(t.id.toString(), on) }
                    }
                }
            }
        }
    }
}

/** A tickable line of text, 48 dp tall, the whole row taking the tap. */
@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().minTouch().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null)
        Text(label, Modifier.padding(start = Space.s))
    }
}

/** The colour of each day's first workout that has one (a day with only uncoloured workouts has none); empty when the colours are off. [workouts] run oldest first. */
fun dayTemplateColors(workouts: List<clean.core.Workout>, colors: TemplateColors): Map<java.time.LocalDate, Int> {
    if (!colors.on) return emptyMap()
    val days = HashMap<java.time.LocalDate, Int>()
    for (w in workouts) {
        val colour = colors.ofName(w.name) ?: continue
        val day = runCatching { java.time.LocalDate.parse(w.day) }.getOrNull() ?: continue
        days.putIfAbsent(day, colour)
    }
    return days
}
