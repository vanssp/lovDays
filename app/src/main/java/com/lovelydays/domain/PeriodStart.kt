package com.lovelydays.domain

import java.time.LocalDate

/**
 * Records that a period started on [start] by moving the profile anchor.
 * Forecasts, the month grid, and reminders all read that same date.
 */
object PeriodStart {
    fun apply(profile: Profile, start: LocalDate, today: LocalDate = LocalDate.now()): Result {
        val issue = ProfileValidator.periodDateIssue(profile.birth, start, today)
        if (issue != null) return Result.Invalid(issue)
        return Result.Updated(profile.copy(lastPeriod = start))
    }

    sealed interface Result {
        data class Updated(val profile: Profile) : Result
        data class Invalid(val reason: IssueCode) : Result
    }
}
