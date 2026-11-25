package clean.core

import clean.core.time.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime

/** The common-Kotlin replacements for java.time, BigDecimal and Math.round give what the java versions gave. */
class JavaParityTest {
    private fun samples(): List<Double> {
        val r = Random(42)
        val list = mutableListOf(0.0, -0.0, 0.5, 2.675, 1.005, 0.0078125, 0.0234375, 62.5, 1e-9, -1e-9, 54.4311, 123456.789, 0.1, 0.3, 1e10, 4.35, 12.0, 100.0)
        repeat(20000) { list += (r.nextInt(2_000_000) - 1_000_000) / 1000.0 / (1 shl r.nextInt(8)) }
        repeat(20000) { list += r.nextDouble() * Math.pow(10.0, r.nextInt(12) - 4.0) }
        repeat(5000) { list += r.nextInt(100000) / 128.0 }
        return list
    }

    @Test
    fun `rounded decimals match BigDecimal`() {
        for (x in samples()) for (scale in listOf(2, 4, 6)) {
            val big = BigDecimal(x).setScale(scale, RoundingMode.HALF_UP)
            assertEquals(big.unscaledValue().toLong(), roundedUnscaled(x, scale), "$x at $scale")
            val stripped = big.stripTrailingZeros()
            assertEquals(if (stripped.signum() == 0) "0" else stripped.toPlainString(), plainDecimal(x, scale), "$x plain at $scale")
        }
    }

    @Test
    fun `jRound matches Math round`() {
        val odd = listOf(0.5, -0.5, 1.5, -1.5, 2.5, -2.5, 0.49999999999999994, 1e15 + 0.5, Double.NaN, 1e300, -1e300)
        for (x in odd + samples()) assertEquals(Math.round(x), jRound(x), "$x")
    }

    @Test
    fun `date patterns match DateTimeFormatter`() {
        val r = Random(7)
        val patterns = listOf("yyyy-MM-dd HHmm", "yyyy-MM-dd HH:mm:ss", "yyyyMMdd-HHmmss", "yyyy:MM:dd HH:mm:ss", "d MMM yyyy, HH:mm", "MMM", "MMM yyyy", "d MMM", "d MMM yyyy")
        repeat(3000) {
            val at = java.time.LocalDateTime.of(1990 + r.nextInt(60), 1 + r.nextInt(12), 1 + r.nextInt(28), r.nextInt(24), r.nextInt(60), r.nextInt(60))
            val mine = at.toKotlinLocalDateTime()
            for (p in patterns) {
                val java = at.format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH))
                assertEquals(java, mine.format(DatePattern(p)), p)
            }
            val strong = DatePattern("yyyy-MM-dd HH:mm:ss")
            assertEquals(mine, strong.parse(strong.format(mine)))
        }
        // The SMART style of java moves a day past the month's end back to it, and reads 24:00:00 as midnight after.
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        for (text in listOf("2025-02-30 10:00:00", "2024-02-30 10:00:00", "2025-04-31 01:02:03", "2025-02-28 24:00:00", "2025-02-28 24:00:01", "2025-13-01 00:00:00", "2025-00-10 00:00:00", "0000-01-01 00:00:00", "2025-1-01 00:00:00", "2025-01-01 10:00:60")) {
            val java = runCatching { java.time.LocalDateTime.parse(text, f).toKotlinLocalDateTime() }.getOrNull()
            assertEquals(java, STRONG_DATE_FORMAT.parseOrNull(text), text)
        }
    }

    @Test
    fun `english names match Locale ENGLISH`() {
        for (m in java.time.Month.entries) {
            assertEquals(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH), kotlinx.datetime.Month.valueOf(m.name).englishName())
            assertEquals(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), kotlinx.datetime.Month.valueOf(m.name).englishName(false))
        }
        for (d in java.time.DayOfWeek.entries) {
            assertEquals(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), kotlinx.datetime.DayOfWeek.valueOf(d.name).englishName(false))
            assertEquals(d.getDisplayName(TextStyle.FULL, Locale.ENGLISH), kotlinx.datetime.DayOfWeek.valueOf(d.name).englishName())
        }
    }

    @Test
    fun `calendar arithmetic matches java time`() {
        val r = Random(3)
        repeat(5000) {
            val a = java.time.LocalDate.of(1995 + r.nextInt(40), 1 + r.nextInt(12), 1 + r.nextInt(28))
            val b = a.plusDays(r.nextInt(4000) - 2000L)
            val ka = a.toKotlinLocalDate(); val kb = b.toKotlinLocalDate()
            assertEquals(java.time.temporal.ChronoUnit.DAYS.between(a, b), ChronoUnit.DAYS.between(ka, kb))
            assertEquals(java.time.temporal.ChronoUnit.MONTHS.between(a, b), ChronoUnit.MONTHS.between(ka, kb))
            assertEquals(a.plusMonths(7).plusYears(1), ka.plusMonths(7).plusYears(1).toJavaLocalDate())
            assertEquals(a.withDayOfMonth(1).plusMonths(1).minusDays(1).dayOfMonth, ka.withDayOfMonth(1).plusMonths(1).minusDays(1).lengthOfMonth() - 0)
            assertEquals(a.lengthOfMonth(), ka.lengthOfMonth())
            assertEquals(a.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), weekStart(ka).toJavaLocalDate())
            val t = a.atTime(r.nextInt(24), r.nextInt(60), r.nextInt(60))
            val kt = t.let { kotlinx.datetime.LocalDateTime(ka, kotlinx.datetime.LocalTime(it.hour, it.minute, it.second)) }
            assertEquals(t.plusSeconds(123456), kt.plusSeconds(123456).toJavaLocalDateTime())
            assertEquals(t.minusMinutes(5000), kt.minusMinutes(5000).toJavaLocalDateTime())
            assertEquals(t.toEpochSecond(java.time.ZoneOffset.UTC), kt.toEpochSecondUtc())
        }
    }
}
