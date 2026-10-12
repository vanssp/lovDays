package com.lovelydays.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class PlannedReminder(
    val workName: String,
    val dedupeKey: String,
    val type: String,
    val phase: String,
    val eventDay: LocalDate,
    val delayMillis: Long,
)

object ReminderPlanner {
    fun plan(
        userId: Long,
        profile: Profile,
        prefs: NotificationPrefs,
        now: ZonedDateTime,
        fired: Set<String>,
        horizonDays: Long = 60,
        graceHours: Long = 6,
    ): List<PlannedReminder> {
        val today = now.toLocalDate()
        val events = eventDays(profile, prefs, today.minusDays(1), today.plusDays(horizonDays))
        val zone = now.zone
        val grace = Duration.ofHours(graceHours)
        val result = mutableListOf<PlannedReminder>()
        for ((type, day) in events) {
            for (phase in listOf("eve", "day")) {
                val triggerDay = if (phase == "eve") day.minusDays(1) else day
                val trigger = triggerDay.atTime(9, 0).atZone(zone)
                val key = dedupeKey(userId, type, phase, day)
                if (key in fired) continue
                val delay = Duration.between(now, trigger)
                if (delay.isNegative && delay.abs() > grace) continue
                result += PlannedReminder(
                    workName = "ld-$userId-$type-$phase-${day.toEpochDay()}",
                    dedupeKey = key,
                    type = type,
                    phase = phase,
                    eventDay = day,
                    delayMillis = delay.toMillis().coerceAtLeast(0L),
                )
            }
        }
        return result
    }

    fun stillRelevant(
        profile: Profile,
        prefs: NotificationPrefs,
        type: String,
        phase: String,
        eventDay: LocalDate,
        today: LocalDate,
    ): Boolean {
        if (!prefs.allows(type)) return false
        val events = eventDays(profile, prefs, today.minusDays(2), today.plusDays(70))
        if (events.none { it.first == type && it.second == eventDay }) return false
        val triggerDay = if (phase == "eve") eventDay.minusDays(1) else eventDay
        if (triggerDay.isAfter(today)) return false
        return ChronoUnit.DAYS.between(triggerDay, today) <= 1
    }

    fun dedupeKey(userId: Long, type: String, phase: String, eventDay: LocalDate): String =
        "$userId|$type|$phase|${eventDay.toEpochDay()}"

    private fun eventDays(
        profile: Profile,
        prefs: NotificationPrefs,
        from: LocalDate,
        until: LocalDate,
    ): List<Pair<String, LocalDate>> {
        val resolved = CycleCalculator.resolve(profile, from)
        val cycles = CycleCalculator.project(
            resolved.params,
            until.plusDays(resolved.params.cycleLength.toLong()),
        )
        val out = mutableListOf<Pair<String, LocalDate>>()
        for (cycle in cycles) {
            if (prefs.period) out += "period" to cycle.period.start
            if (prefs.ovulation) out += "ovulation" to cycle.ovulation
            if (prefs.mood) out += "mood" to cycle.pmsMood.start
            if (prefs.skin) out += "skin" to cycle.skin.start
        }
        return out.filter { (_, day) -> !day.isBefore(from) && !day.isAfter(until) }
    }
}

object ReminderCopy {
    fun title(type: String, phase: String): String = when (type to phase) {
        "period" to "eve" -> "Скоро возможная менструация"
        "period" to "day" -> "Сегодня возможное начало менструации"
        "ovulation" to "eve" -> "Скоро ориентировочная овуляция"
        "ovulation" to "day" -> "Сегодня ориентировочная овуляция"
        "mood" to "eve" -> "Скоро окно смены настроения"
        "mood" to "day" -> "Сегодня может меняться настроение"
        "skin" to "eve" -> "Скоро кожа может реагировать сильнее"
        "skin" to "day" -> "Сегодня кожа может быть чувствительнее"
        else -> "Эти дни"
    }

    fun body(type: String, phase: String, eventDay: LocalDate): String {
        val date = Russian.day(eventDay)
        val tail = if (type == "ovulation") "Это не контрацепция." else "Оценка, не медицинский совет."
        val whenText = if (phase == "eve") "Напоминание за день." else "Напоминание в этот день."
        return "Ориентировочная дата — $date. $whenText $tail"
    }
}
