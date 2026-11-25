package clean.core

import clean.core.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

/** The quick reading of a stored date gives exactly what the formatter gives, for real dates and for text that is not one. */
class ParseDateTest {
    private fun formatter(text: String): LocalDateTime? = runCatching { LocalDateTime.parse(text, STRONG_DATE_FORMAT) }.getOrNull()

    @Test fun `same as the formatter`() {
        val texts = mutableListOf(
            "2024-02-29 07:05:09", "2023-02-29 07:05:09", "2023-02-30 10:00:00", "2023-04-31 10:00:00", "2023-12-31 23:59:59",
            "2023-13-01 10:00:00", "2023-00-10 10:00:00", "2023-01-00 10:00:00", "2023-01-01 24:00:00", "2023-01-01 10:60:00",
            "2023-01-01 10:00:60", "2023-1-01 10:00:00", "2023-01-01T10:00:00", "", "abcd-ef-gh ij:kl:mn", "0000-01-01 00:00:00",
            "2023-01-01 10:00:00 ", "+202-01-01 10:00:00",
        )
        var day = LocalDateTime.of(2020, 1, 1, 0, 0, 0)
        while (day.year < 2025) { texts += day.format(STRONG_DATE_FORMAT); day = day.plusHours(13).plusMinutes(7).plusSeconds(11) }
        for (t in texts) assertEquals(formatter(t), parseStrongDate(t), t)
    }
}
