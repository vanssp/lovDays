package com.lovelydays.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleLogicTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun ovulationIsFourteenDaysBeforeNextPeriod() {
        val params = CycleParams(start, cycleLength = 28, periodLength = 5)
        val cycle = CycleCalculator.cycleAt(0, start, params)
        assertEquals(LocalDate.of(2026, 1, 1), cycle.period.start)
        assertEquals(LocalDate.of(2026, 1, 5), cycle.period.endInclusive)
        assertEquals(LocalDate.of(2026, 1, 29), start.plusDays(28))
        assertEquals(LocalDate.of(2026, 1, 15), cycle.ovulation)
        assertEquals(LocalDate.of(2026, 1, 10), cycle.fertile.start)
        assertEquals(LocalDate.of(2026, 1, 15), cycle.fertile.endInclusive)
        assertEquals(LocalDate.of(2025, 12, 27), cycle.pmsMood.start)
        assertEquals(LocalDate.of(2025, 12, 31), cycle.pmsMood.endInclusive)
        assertEquals(LocalDate.of(2025, 12, 29), cycle.skin.start)
        assertEquals(LocalDate.of(2026, 1, 2), cycle.skin.endInclusive)
        assertEquals(LocalDate.of(2026, 1, 14), cycle.midCycleMood.start)
        assertEquals(LocalDate.of(2026, 1, 16), cycle.midCycleMood.endInclusive)
    }

    @Test
    fun marksOverlapWithoutDroppingAny() {
        val cycles = CycleCalculator.project(CycleParams(start, 28, 5), LocalDate.of(2026, 3, 1))
        assertEquals(listOf(Mark.PERIOD, Mark.SKIN), CycleCalculator.marksOn(LocalDate.of(2026, 1, 1), cycles))
        assertEquals(
            listOf(Mark.OVULATION, Mark.FERTILE, Mark.MOOD),
            CycleCalculator.marksOn(LocalDate.of(2026, 1, 15), cycles),
        )
        assertEquals(listOf(Mark.FERTILE), CycleCalculator.marksOn(LocalDate.of(2026, 1, 12), cycles))
        assertEquals(
            listOf(Mark.MOOD, Mark.SKIN),
            CycleCalculator.marksOn(LocalDate.of(2025, 12, 30), cycles),
        )
    }

    @Test
    fun assumedLengthUsesAgeAndBmiWithoutFakePrecision() {
        assertEquals(32, BodyMetrics.assumedCycleLength(ageYears = 16, bmi = 22.0))
        assertEquals(34, BodyMetrics.assumedCycleLength(ageYears = 16, bmi = 31.0))
        assertEquals(30, BodyMetrics.assumedCycleLength(ageYears = 21, bmi = 22.0))
        assertEquals(28, BodyMetrics.assumedCycleLength(ageYears = 30, bmi = 22.0))
        assertEquals(29, BodyMetrics.assumedCycleLength(ageYears = 30, bmi = 17.0))
        assertEquals(BodyMetrics.Level.TYPICAL, BodyMetrics.confidence(30, 22.0).level)
        assertEquals(BodyMetrics.Level.LOWER, BodyMetrics.confidence(16, 22.0).level)
        assertTrue(BodyMetrics.confidence(47, 22.0).note.contains("45"))
        assertTrue(BodyMetrics.confidence(30, 32.0).note.contains("18,5"))
    }

    @Test
    fun forecastNamesTheAssumption() {
        val profile = Profile(
            userId = 1,
            birth = LocalDate.of(2005, 1, 1),
            weightKg = 60.0,
            heightCm = 165.0,
            lastPeriod = LocalDate.of(2026, 5, 1),
            cycleLength = null,
            periodLength = null,
        )
        val forecast = ForecastBuilder.build(profile, today = LocalDate.of(2026, 6, 10))
        assertTrue(forecast.cycleNote.contains("не задана"))
        assertTrue(forecast.cycleNote.contains("30"))
        assertTrue(forecast.periodLengthNote.contains("5"))
        assertTrue(forecast.disclaimer.contains("не средство контрацепции"))
        assertEquals("Следующая менструация", forecast.periodTitle)
    }

    @Test
    fun validatorRejectsFuturePeriodAndAcceptsEmptyCycle() {
        val today = LocalDate.of(2026, 6, 1)
        val bad = ProfileValidator.validate(
            birth = LocalDate.of(1996, 1, 1),
            weightRaw = "60",
            heightRaw = "168",
            lastPeriod = today.plusDays(1),
            cycleRaw = "",
            periodRaw = "",
            today = today,
        )
        assertEquals(IssueCode.DATE_IN_FUTURE, bad[ProfileField.LAST_PERIOD])
        val good = ProfileValidator.validate(
            birth = LocalDate.of(1996, 1, 1),
            weightRaw = "60,5",
            heightRaw = "168",
            lastPeriod = today.minusDays(10),
            cycleRaw = "",
            periodRaw = "",
            today = today,
        )
        assertTrue(good.isEmpty())
        assertEquals(60.5, ProfileValidator.parseDecimal("60,5")!!, 0.001)
    }

    @Test
    fun mondayFirstGridPadsTheMonth() {
        val may = java.time.YearMonth.of(2026, 5)
        val cells = MonthGrid.cells(may)
        assertEquals(0, cells.size % 7)
        assertNull(cells.first())
        assertEquals(LocalDate.of(2026, 5, 1), cells[4])
    }

    @Test
    fun plannerSchedulesEveAndDayForOvulation() {
        val today = LocalDate.of(2026, 6, 10)
        val profile = Profile(
            userId = 7,
            birth = LocalDate.of(1994, 4, 2),
            weightKg = 62.0,
            heightCm = 168.0,
            lastPeriod = today.minusDays(10),
            cycleLength = 28,
            periodLength = 5,
        )
        val now = ZonedDateTime.of(today.atTime(8, 0), ZoneId.of("Europe/Moscow"))
        val planned = ReminderPlanner.plan(
            userId = 7,
            profile = profile,
            prefs = NotificationPrefs.allOn(7),
            now = now,
            fired = emptySet(),
        )
        val ovulation = today.plusDays(4)
        val eve = planned.find { it.type == "ovulation" && it.phase == "eve" && it.eventDay == ovulation }
        val day = planned.find { it.type == "ovulation" && it.phase == "day" && it.eventDay == ovulation }
        assertTrue(eve != null && eve.delayMillis > 0L)
        assertTrue(day != null && day.delayMillis > eve!!.delayMillis)
        assertFalse(planned.any { it.dedupeKey in setOf(ReminderPlanner.dedupeKey(7, "ovulation", "eve", ovulation)) && it.delayMillis < 0 })
        assertEquals(
            "Скоро ориентировочная овуляция",
            ReminderCopy.title("ovulation", "eve"),
        )
    }
}
