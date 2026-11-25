package clean.app.android

import clean.app.*

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon follows the theme: the manifest has one activity-alias per theme (`.IconGraphite`, `.IconEmber`, ...), each with the
 * icon whose ring is that theme's accent, and exactly one is enabled. It is applied only when the app stops (MainActivity.onStop, not for
 * a rotation), never while the app is in front: disabling the alias the app's task was started through makes Android remove that task
 * even with DONT_KILL_APP, which looked like a crash when the theme was changed in Settings. So the new icon appears once the app is
 * left, and the switch may close the app's task in the background (coming back then starts fresh on Home). The alias is also the launcher
 * activity the shortcuts belong to, so the caller publishes them again after a change (AndroidPlatform.applyAppIcon).
 */
object AppIcon {
    private val NAMES = listOf("Graphite", "Ember", "Crimson", "Moss", "Ocean", "Plum", "Ice", "Rose")

    /**
     * The icon each theme wears. Round 34 made twelve themes but drew no new icons: a theme takes the icon of the nearest of the eight (Slate wears Graphite's, Saffron
     * Ember's, Green and Teal Moss's, Lagoon Ice's, Cobalt Ocean's, Iris Plum's, Orchid Rose's); the old names stay so a stored one still has an icon.
     */
    private val ICON_OF = NAMES.associateWith { it } + mapOf(
        "Slate" to "Graphite", "Saffron" to "Ember", "Green" to "Moss", "Teal" to "Moss", "Lagoon" to "Ice", "Cobalt" to "Ocean", "Iris" to "Plum", "Orchid" to "Rose",
    )

    /** The alias class name for a theme; an unknown name gives Graphite's. */
    fun aliasOf(theme: String): String = ".Icon" + (ICON_OF[theme] ?: "Graphite")

    fun aliases(): List<String> = NAMES.map { aliasOf(it) }

    private fun componentOf(context: Context, alias: String) = ComponentName(context.packageName, "clean.app$alias")

    private fun isEnabled(pm: PackageManager, component: ComponentName, alias: String): Boolean {
        val now = pm.getComponentEnabledSetting(component)
        // The manifest's own default (enabled only for Graphite) counts as already right.
        return now == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == ".IconGraphite")
    }

    /** The alias that is enabled now (Graphite's when none says so); the activity launcher shortcuts name. */
    fun enabledComponent(context: Context): ComponentName {
        val pm = context.packageManager
        return componentOf(context, aliases().firstOrNull { isEnabled(pm, componentOf(context, it), it) } ?: ".IconGraphite")
    }

    /** Enables the theme's alias and disables the others; true when anything changed. */
    fun applyIcon(context: Context, theme: String): Boolean {
        val pm = context.packageManager
        val want = aliasOf(theme)
        var changed = false
        for (alias in aliases()) {
            val component = componentOf(context, alias)
            if (isEnabled(pm, component, alias) == (alias == want)) continue
            val state = if (alias == want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            changed = true
        }
        return changed
    }
}
