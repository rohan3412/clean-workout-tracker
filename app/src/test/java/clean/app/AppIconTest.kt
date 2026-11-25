package clean.app

import org.junit.experimental.categories.Category
import clean.app.android.*

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher icon follows the theme: a theme names one alias, and exactly one alias is enabled. */
@Category(Regression::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppIconTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun enabled(): List<String> = AppIcon.aliases().filter {
        val s = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, "clean.app$it"))
        s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    @Test fun `a theme names its alias and an unknown one gives graphite`() {
        assertEquals(".IconEmber", AppIcon.aliasOf("Ember"))
        assertEquals(".IconRose", AppIcon.aliasOf("Rose"))
        assertEquals(".IconGraphite", AppIcon.aliasOf("Nope"))
        assertEquals(8, AppIcon.aliases().toSet().size)
    }

    @Test fun `applying a theme enables exactly its alias`() {
        AppIcon.applyIcon(context, "Plum")
        assertEquals(listOf(".IconPlum"), enabled())
        AppIcon.applyIcon(context, "Plum")
        assertEquals(listOf(".IconPlum"), enabled())
        AppIcon.applyIcon(context, "Ice")
        assertEquals(listOf(".IconIce"), enabled())
        AppIcon.applyIcon(context, "unknown")
        assertEquals(listOf(".IconGraphite"), enabled())
    }
}
