package com.lovelydays.domain

import java.time.LocalDate
import java.time.YearMonth

object CycleCalculator {
    /** Typical luteal phase: ovulation sits this many days before the next period. */
    const val LUTEAL_DAYS = 14

    fun resolve(profile: Profile, today: LocalDate): ResolvedCycle {
        val age = BodyMetrics.ageYears(profile.birth, today)
        val bmi = BodyMetrics.bmi(profile.weightKg, profile.heightCm)
        val cycleLength = (profile.cycleLength ?: BodyMetrics.assumedCycleLength(age, bmi)).coerceIn(21, 45)
        val periodLength = (profile.periodLength ?: BodyMetrics.DEFAULT_PERIOD_LENGTH).coerceIn(2, 10)
        return ResolvedCycle(
            params = CycleParams(profile.lastPeriod, cycleLength, periodLength),
            ageYears = age,
            bmi = bmi,
            cycleLengthAssumed = profile.cycleLength == null,
            periodLengthAssumed = profile.periodLength == null,
            confidence = BodyMetrics.confidence(age, bmi),
        )
    }

    fun project(params: CycleParams, until: LocalDate): List<PredictedCycle> {
        val cycleLength = params.cycleLength.coerceIn(21, 45)
        val periodLength = params.periodLength.coerceIn(2, 10)
        val safe = params.copy(cycleLength = cycleLength, periodLength = periodLength)
        val result = mutableListOf<PredictedCycle>()
        var start = safe.lastPeriodStart
        var index = 0
        val horizon = until.plusDays(cycleLength.toLong() + 1)
        while (!start.isAfter(horizon) && index < 40) {
            result += cycleAt(index, start, safe)
            start = start.plusDays(cycleLength.toLong())
            index++
        }
        return result
    }

    fun cycleAt(index: Int, periodStart: LocalDate, params: CycleParams): PredictedCycle {
        val period = DateRange(
            start = periodStart,
            endInclusive = periodStart.plusDays((params.periodLength - 1).toLong()),
        )
        val nextStart = periodStart.plusDays(params.cycleLength.toLong())
        val ovulation = nextStart.minusDays(LUTEAL_DAYS.toLong())
        return PredictedCycle(
            index = index,
            period = period,
            ovulation = ovulation,
            fertile = DateRange(ovulation.minusDays(5), ovulation),
            pmsMood = DateRange(periodStart.minusDays(5), periodStart.minusDays(1)),
            midCycleMood = DateRange(ovulation.minusDays(1), ovulation.plusDays(1)),
            skin = DateRange(periodStart.minusDays(3), periodStart.plusDays(1)),
        )
    }

    fun marksOn(date: LocalDate, cycles: List<PredictedCycle>): List<Mark> {
        val marks = linkedSetOf<Mark>()
        for (cycle in cycles) {
            if (cycle.period.contains(date)) marks += Mark.PERIOD
            if (cycle.ovulation == date) marks += Mark.OVULATION
            if (cycle.fertile.contains(date)) marks += Mark.FERTILE
            if (cycle.pmsMood.contains(date) || cycle.midCycleMood.contains(date)) marks += Mark.MOOD
            if (cycle.skin.contains(date)) marks += Mark.SKIN
        }
        return marks.toList()
    }
}

object MonthGrid {
    /** Monday-first month cells. Nulls pad the leading and trailing week. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val shift = first.dayOfWeek.value - 1
        val cells = MutableList<LocalDate?>(shift) { null }
        for (day in 1..month.lengthOfMonth()) {
            cells += month.atDay(day)
        }
        while (cells.size % 7 != 0) {
            cells += null
        }
        return cells
    }
}
