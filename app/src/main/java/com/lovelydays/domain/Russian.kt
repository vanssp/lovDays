package com.lovelydays.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Russian {
    private val locale: Locale = Locale.forLanguageTag("ru")
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    fun day(date: LocalDate): String = dayMonth.format(date)

    fun full(date: LocalDate): String = dayMonthYear.format(date)

    fun month(month: YearMonth): String {
        val raw = monthYear.format(month.atDay(1))
        return raw.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }

    fun range(start: LocalDate, end: LocalDate): String {
        if (start == end) return day(start)
        if (start.year == end.year && start.month == end.month) {
            return "${start.dayOfMonth}–${day(end)}"
        }
        if (start.year == end.year) {
            return "${day(start)} – ${day(end)}"
        }
        return "${full(start)} – ${full(end)}"
    }

    fun number1(value: Double): String = String.format(locale, "%.1f", value)

    fun days(n: Int): String = plural(n, "день", "дня", "дней")

    fun years(n: Int): String = plural(n, "год", "года", "лет")

    fun events(n: Int): String = plural(n, "событие", "события", "событий")

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val mod = abs(n) % 100
        val last = mod % 10
        return when {
            mod in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }
}
