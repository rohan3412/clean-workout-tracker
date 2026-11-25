package clean.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import clean.core.SetTarget
import clean.core.SetType
import clean.core.TemplateBlock
import clean.app.data.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** How a template's values are stored: inside the blocks JSON, and absent for a template without any. */
@RunWith(AndroidJUnit4::class)
class TemplateTargetsTest {
    private val old = """[{"exercise":"Pull Up","sets":["W","","F"]},{"exercise":"Row","sets":[""],"superset":"a"}]"""

    @Test
    fun aTemplateWithoutValuesIsStoredAsBefore() {
        val blocks = listOf(
            TemplateBlock("Pull Up", listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE)),
            TemplateBlock("Row", listOf(SetType.NORMAL), "a"),
        )
        assertEquals(old, Template(1, "Pull", blocks).blocksJson().toString())
        // Empty values count as none.
        val empty = blocks.map { it.copy(targets = listOf(null, SetTarget(null, null))) }
        assertEquals(old, Template(1, "Pull", empty).blocksJson().toString())
    }

    @Test
    fun anOldStringReadsWithNoValues() {
        val blocks = Template.blocksOf(JSONArray(old))
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE), blocks[0].sets)
        assertEquals(emptyList<SetTarget?>(), blocks[0].targets)
        assertEquals("a", blocks[1].superset)
    }

    @Test
    fun valuesRoundTrip() {
        val blocks = listOf(
            TemplateBlock("Bench Press", listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL), null, listOf(SetTarget(40.0, 8), SetTarget(82.5, null), null)),
            TemplateBlock("Push Up", listOf(SetType.NORMAL, SetType.NORMAL), "b", listOf(null, SetTarget(null, 12))),
        )
        val text = Template(1, "Push", blocks).blocksJson().toString()
        val back = Template.blocksOf(JSONArray(text))
        // A trailing set with no values is not written; the rest come back as they were.
        assertEquals(listOf(SetTarget(40.0, 8), SetTarget(82.5, null)), back[0].targets)
        assertEquals(blocks[1], back[1])
        assertEquals(blocks[0].sets, back[0].sets)
    }
}
