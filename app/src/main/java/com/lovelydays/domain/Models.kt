package com.lovelydays.domain

import java.time.LocalDate

data class Profile(
    val userId: Long,
    val birth: LocalDate,
    val weightKg: Double,
    val heightCm: Double,
    val lastPeriod: LocalDate,
    val cycleLength: Int?,
    val periodLength: Int?,
)

data class CycleParams(
    val lastPeriodStart: LocalDate,
    val cycleLength: Int,
    val periodLength: Int,
)

data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!endInclusive.isBefore(start)) {
            "Range ends before it starts: $start .. $endInclusive"
        }
    }

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    fun overlaps(other: DateRange): Boolean =
        !endInclusive.isBefore(other.start) && !other.endInclusive.isBefore(start)
}

enum class Mark {
    PERIOD,
    OVULATION,
    FERTILE,
    MOOD,
    SKIN,
}

data class PredictedCycle(
    val index: Int,
    val period: DateRange,
    val ovulation: LocalDate,
    val fertile: DateRange,
    val pmsMood: DateRange,
    val midCycleMood: DateRange,
    val skin: DateRange,
)

data class ResolvedCycle(
    val params: CycleParams,
    val ageYears: Int,
    val bmi: Double,
    val cycleLengthAssumed: Boolean,
    val periodLengthAssumed: Boolean,
    val confidence: BodyMetrics.Confidence,
)

data class NotificationPrefs(
    val userId: Long,
    val period: Boolean,
    val ovulation: Boolean,
    val mood: Boolean,
    val skin: Boolean,
) {
    fun allows(type: String): Boolean = when (type) {
        "period" -> period
        "ovulation" -> ovulation
        "mood" -> mood
        "skin" -> skin
        else -> false
    }

    companion object {
        fun allOn(userId: Long) = NotificationPrefs(
            userId = userId,
            period = true,
            ovulation = true,
            mood = true,
            skin = true,
        )
    }
}
